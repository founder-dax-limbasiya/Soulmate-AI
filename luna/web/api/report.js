module.exports = function report(req,res) {
  if(req.method!=='POST') return res.status(405).end();
  const category=req.body?.category;
  if(!['Sexual content','Abuse or harm','Manipulative response','Other concern'].includes(category)) return res.status(400).end();
  console.info('companion-content-report',category);
  return res.status(202).json({received:true});
};
