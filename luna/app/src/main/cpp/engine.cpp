#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <mutex>
#include <memory>
#include <vector>
#include <string>

static std::atomic<bool> stopped{false};
static std::mutex session;
extern "C" JNIEXPORT void JNICALL Java_studio_luna_Engine_cancel(JNIEnv*, jobject) { stopped = true; }
extern "C" JNIEXPORT void JNICALL Java_studio_luna_Engine_arm(JNIEnv*, jobject) { stopped = false; }
static void fail(JNIEnv* e, const char* message) {
    e->ThrowNew(e->FindClass("java/lang/IllegalStateException"), message);
}
extern "C" JNIEXPORT void JNICALL Java_studio_luna_Engine_generate(JNIEnv* e, jobject,
        jstring path, jbyteArray input, jint window, jint threads, jobject sink) {
    std::lock_guard<std::mutex> lock(session);
    static std::once_flag init;
    std::call_once(init, []{ llama_backend_init(); });
    auto p = e->GetStringUTFChars(path,nullptr);
    auto mp = llama_model_default_params();
    mp.progress_callback = [](float,void*) { return !stopped.load(); };
    std::unique_ptr<llama_model,decltype(&llama_model_free)> model(llama_model_load_from_file(p,mp),llama_model_free);
    e->ReleaseStringUTFChars(path,p);
    if(stopped) return;
    if(!model) return fail(e,"Could not load model. Close other apps or install the compact model.");
    auto cp = llama_context_default_params();
    cp.n_ctx=window; cp.n_batch=128; cp.n_ubatch=128; cp.n_threads=threads; cp.n_threads_batch=threads;
    cp.abort_callback=[](void*){return stopped.load();};
    std::unique_ptr<llama_context,decltype(&llama_free)> ctx(llama_init_from_model(model.get(),cp),llama_free);
    if(!ctx) return fail(e,"Insufficient memory for this context.");
    auto vocab=llama_model_get_vocab(model.get());
    std::string prompt(e->GetArrayLength(input),' ');
    e->GetByteArrayRegion(input,0,prompt.size(),reinterpret_cast<jbyte*>(prompt.data()));
    int n=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,false,true);
    if(n<=0 || n>window-96) return fail(e,"Context full");
    std::vector<llama_token> tokens(n);
    if(llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),n,false,true)!=n) return fail(e,"Tokenization failed.");
    for(int i=0;i<n && !stopped;i+=128) {
        auto batch=llama_batch_get_one(tokens.data()+i,std::min(128,n-i));
        if(llama_decode(ctx.get(),batch)!=0) { if(stopped) return; return fail(e,"Prompt processing failed."); }
    }
    std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
    llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(32));
    llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(0.75f));
    llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    auto method=e->GetMethodID(e->GetObjectClass(sink),"piece","([B)V");
    if(!method) return;
    for(int i=0;i<std::min(192,window-n) && !stopped;i++) {
        auto token=llama_sampler_sample(sampler.get(),ctx.get(),-1);
        if(llama_vocab_is_eog(vocab,token)) break;
        std::vector<char> data(32);
        int len=llama_token_to_piece(vocab,token,data.data(),data.size(),0,false);
        if(len<0){data.resize(-len);len=llama_token_to_piece(vocab,token,data.data(),data.size(),0,false);}
        if(len>0){
            auto bytes=e->NewByteArray(len);
            e->SetByteArrayRegion(bytes,0,len,reinterpret_cast<jbyte*>(data.data()));
            e->CallVoidMethod(sink,method,bytes); e->DeleteLocalRef(bytes);
            if(e->ExceptionCheck()) return;
        }
        if(llama_decode(ctx.get(),llama_batch_get_one(&token,1))!=0){if(stopped) return;return fail(e,"Generation interrupted.");}
    }
}
