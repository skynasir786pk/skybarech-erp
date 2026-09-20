/* Offline presentation-only localization. Stored records and option values stay canonical. */
(function(root){
 'use strict';
 const catalog=root.SKY_TRANSLATIONS||{};
 const normalize=s=>String(s??'').replace(/\s+/g,' ').trim();
 const lower=new Map(Object.keys(catalog).map(k=>[k.toLowerCase(),k]));
 const missing=new Set();
 let language='en';try{language=localStorage.getItem('skybarech-language')==='ur'?'ur':'en'}catch{}
 const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const patterns=Object.keys(catalog).filter(k=>/\{\d+\}/.test(k)&&k.replace(/\{\d+\}/g,'').trim()).map(key=>{
  const order=[];const parts=key.split(/(\{\d+\})/).map(p=>{if(/^\{\d+\}$/.test(p)){order.push(Number(p.slice(1,-1)));return '(.*?)'}return p.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')});
  return {key,order,re:new RegExp('^'+parts.join('')+'$','i')};
 }).sort((a,b)=>b.key.replace(/\{\d+\}/g,'').length-a.key.replace(/\{\d+\}/g,'').length);
 function t(value){
  const raw=String(value??''),key=normalize(raw);let known=catalog[key]?key:lower.get(key.toLowerCase());
  if(known)return catalog[known][language]??catalog[known].en??raw;
  if(!/\{\d+\}/.test(key)) for(const p of patterns){const m=key.match(p.re);if(m){const values={};p.order.forEach((n,i)=>values[n]=m[i+1]);return (catalog[p.key][language]||p.key).replace(/\{(\d+)\}/g,(_,n)=>values[n]??'')}}
  if(/[A-Za-z]/.test(key))missing.add(key);
  return raw;
 }
 function textPart(raw){
  if(!raw.trim())return raw;
  let indices=[];const key=normalize(raw).replace(/__SKY_VAR_(\d+)__/g,(_,i)=>'{'+(indices.push(i)-1)+'}');
  let translated=t(key);
  if(translated===key){ // Decorative icons can occur on either side of a static label.
   const m=key.match(/^((?:\{\d+\}\s*)*)(.*?)(\s*(?:\{\d+\}\s*)*)$/);
   if(m&&m[2]&&/[A-Za-z]/.test(m[2]))translated=m[1]+t(m[2])+m[3];
  }
  translated=translated.replace(/\{(\d+)\}/g,(_,n)=>`__SKY_VAR_${indices[n]}__`);
  return (raw.match(/^\s*/)?.[0]||'')+escape(translated)+(raw.match(/\s*$/)?.[0]||'');
 }
 function localizeMarkup(markup){
  // Explicit option values are required: translating a label must not change submitted data.
  markup=markup.replace(/<option\b([^>]*)>([^<]*)<\/option>/gi,(full,attrs,text)=> /\bvalue\s*=/.test(attrs)?full:`<option${attrs} value="${text.replace(/"/g,'&quot;')}">${text}</option>`);
  return markup.replace(/>([^<>]+)</g,(_,text)=>'>'+textPart(text)+'<')
   .replace(/\b(placeholder|title|aria-label|alt)="([^"]*)"/g,(_,attr,text)=>`${attr}="${textPart(text)}"`);
 }
 function localizeSelects(markup) {
  return markup.replace(/<select\b([^>]*)>([\s\S]*?)<\/select>/gi,(whole,attrs,body)=>{
   const name=attrs.match(/\bname="([^"]+)"/)?.[1];
   if(!['category','quality','status','payment','direction','role','months','variant','warranty','condition','wallet','ram','storage'].includes(name)&&!attrs.includes('data-report-range'))return whole;
   return '<select'+attrs+'>'+body.replace(/(<option\b[^>]*>)([^<]*)(<\/option>)/gi,(_,start,label,end)=>start+escape(t(label))+end)+'</select>';
  });
 }
 function html(strings,...values){
  let template=strings.reduce((out,s,i)=>out+s+(i<values.length?`__SKY_VAR_${i}__`:''),'');
  template=localizeMarkup(template);
  return localizeSelects(template.replace(/__SKY_VAR_(\d+)__/g,(_,i)=>String(values[i]??'')));
 }
 function htmlText(value){return localizeMarkup(value)}
 function apply(){if(typeof document!=='undefined'){document.documentElement.lang=language;document.documentElement.dir=language==='ur'?'rtl':'ltr'}}
 function setLanguage(value){language=value==='ur'?'ur':'en';try{localStorage.setItem('skybarech-language',language)}catch{}apply()}
 root.SkyI18n={t,html,htmlText,setLanguage,get language(){return language},missing};apply();
 if(typeof module!=='undefined')module.exports=root.SkyI18n;
})(typeof window!=='undefined'?window:globalThis);
