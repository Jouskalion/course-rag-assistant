import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const api=JSON.parse(fs.readFileSync(path.join(root,'contracts/openapi.json'),'utf8'));
const check=process.argv.includes('--check');
let bad=false;
function out(file,text){const p=path.join(root,file);if(check){if(!fs.existsSync(p)||fs.readFileSync(p,'utf8')!==text){console.error('契约生成文件不一致: '+file);bad=true;}}else{fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,text);}}
const camel=s=>s.replace(/_([a-z])/g,(_,c)=>c.toUpperCase());
function core(s){return s.anyOf?s.anyOf.find(v=>v.type!=='null'):s;}
function nullable(s){return !!s.anyOf || Array.isArray(s.type)&&s.type.includes('null');}
function jt(s){s=core(s);if(s.$ref)return s.$ref.split('/').at(-1);const t=Array.isArray(s.type)?s.type[0]:s.type;return t==='array'?`List<${jt(s.items)}>`:t==='integer'?'Integer':t==='number'?'Double':t==='boolean'?'Boolean':s.format==='uuid'?'UUID':s.format==='date-time'?'Instant':'String';}
function tt(s){let suffix=nullable(s)?' | null':'';s=core(s);if(s.$ref)return s.$ref.split('/').at(-1)+suffix;const t=Array.isArray(s.type)?s.type[0]:s.type;return (s.enum?s.enum.map(JSON.stringify).join(' | '):t==='array'?`Array<${tt(s.items)}>`:t==='integer'||t==='number'?'number':t==='boolean'?'boolean':'string')+suffix;}
let ts='// 从 contracts/openapi.json 生成。运行 node scripts/generate-contracts.mjs 更新。\n';
for(const [name,schema] of Object.entries(api.components.schemas)){
 const fields=[];const ctor=[];
 ts+=`\nexport interface ${name} {\n`;
 for(const [key,s] of Object.entries(schema.properties)){
  const optional=!schema.required.includes(key),v=core(s),cn=camel(key),typ=jt(s);
  ts+=`  /** ${(s.description||v.description||'').replaceAll('*/','')} */\n  ${key}${optional?'?':''}: ${tt(s)};\n`;
  let a=[`@JsonProperty("${key}")`];
  if(!optional&&!nullable(s))a.push('@NotNull');
  const type=Array.isArray(v.type)?v.type[0]:v.type;
  if(type==='string'&&(v.minLength!==undefined||v.maxLength!==undefined))a.push(`@CodePointLength(min=${v.minLength||0}, max=${v.maxLength||2147483647})`);
  if(type==='array'){
    if(v.minItems!==undefined||v.maxItems!==undefined)a.push(`@Size(min=${v.minItems||0}, max=${v.maxItems||2147483647})`);
    ctor.push(`        ${cn} = ${cn} == null ? null : List.copyOf(${cn});`);
  }
  if(v.enum)a.push(`@Pattern(regexp="${v.enum.join('|')}")`);
  if(type==='integer'&&v.minimum!==undefined)a.push(`@Min(${v.minimum})`);
  if(type==='integer'&&v.maximum!==undefined)a.push(`@Max(${v.maximum})`);
  if(v.$ref||type==='array')a.push('@Valid');
  fields.push(`        ${a.join(' ')} ${typ} ${cn}`);
 }
 ts+='}\n';
 const doc=`// 生成文件；字段修改请先改 contracts/openapi.json。负责人 ${schema['x-owner']}。\npackage com.courseqa.contracts.dto;\n\nimport com.fasterxml.jackson.annotation.JsonProperty;\nimport com.courseqa.common.CodePointLength;\nimport jakarta.validation.Valid;\nimport jakarta.validation.constraints.*;\nimport java.util.List;\nimport java.util.UUID;\nimport java.time.Instant;\n\npublic record ${name}(\n${fields.join(',\n')}\n) {\n${ctor.length?`    public ${name} {\n${ctor.join('\n')}\n    }\n`:''}}\n`;
 out(`backend/src/main/java/com/courseqa/contracts/dto/${name}.java`,doc);
}
out('frontend/src/api/generated.ts',ts);
if(bad)process.exit(1);console.log(check?'接口类型一致':'已生成 Java 与 TypeScript 类型');
