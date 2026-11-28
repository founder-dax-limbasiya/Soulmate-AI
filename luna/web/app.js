const el = id => document.getElementById(id);
const tones = {Caring:'Warm, gentle replies with room to talk through your day.',Playful:'Light teasing and curious questions.',Romantic:'Affectionate, respectful conversation at your pace.',Funny:'Witty observations and little jokes.',Supportive:'Encouragement and patient listening.'};
let prefs = {};
try { prefs = JSON.parse(localStorage.getItem('luna-studio') || '{}') || {}; } catch {}
function save() { try { localStorage.setItem('luna-studio',JSON.stringify(prefs)); } catch {} }
function tone(selected) {
  prefs.tone = selected;
  document.querySelectorAll('.chips button').forEach(b => b.setAttribute('aria-pressed',String(b.textContent === selected)));
  el('toneInfo').textContent = tones[selected]; save();
}
Object.keys(tones).forEach(t => {const b=document.createElement('button');b.textContent=t;b.onclick=()=>tone(t);document.querySelector('.chips').append(b);});
tone(Object.hasOwn(tones,prefs.tone)?prefs.tone:'Caring');
el('name').value = String(prefs.name || 'Luna').slice(0,32);
function nameChanged() { prefs.name=el('name').value; el('caption').textContent=prefs.name.trim() || 'Luna'; save(); }
el('name').oninput=nameChanged;nameChanged();
el('language').selectedIndex=Number(prefs.language)||0;
el('language').onchange=()=>{prefs.language=el('language').selectedIndex;save();};
function estimate(){const compact=el('ram').value==='3';el('modelName').textContent=compact?'Compact · 429 MB':'Balanced · 491 MB';el('spec').textContent=compact?'Qwen2.5 0.5B · Q4_0. Short context helps reduce memory use. Very low available memory may prevent generation.':'Qwen2.5 0.5B · Q4_K_M. Allows a larger context when enough memory is available.';}
el('ram').onchange=estimate;estimate();
document.body.classList.toggle('light',prefs.light===true);el('theme').onclick=()=>{prefs.light=document.body.classList.toggle('light');save();};
