import type {ErrorEnvelope} from './generated';
export class ApiFailure extends Error {
  constructor(public status:number,public payload:ErrorEnvelope){super(payload.error.message);}
}
export async function request<T>(path:string,init:RequestInit={}):Promise<T>{
  const headers=new Headers(init.headers);
  if(init.body && !(init.body instanceof FormData))headers.set('Content-Type','application/json');
  const response=await fetch('/api/v1'+path,{...init,headers});
  if(!response.ok)throw new ApiFailure(response.status,await response.json() as ErrorEnvelope);
  if(response.status===204)return undefined as T;
  return await response.json() as T;
}
