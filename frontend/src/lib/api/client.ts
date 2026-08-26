import type { Page } from "@/lib/types";

export const apiBaseUrl = process.env.API_BASE_URL?.replace(/\/$/, "");
export const fixturePage = <T>(all:T[], page=0, size=20):Page<T> => ({content:all.slice(page*size,(page+1)*size),totalElements:all.length,totalPages:Math.ceil(all.length/size),page,size});
export class ApiError extends Error {
  status:number;
  body:unknown;
  constructor(status:number, body:unknown) {
    super(`Backend request failed (${status})`);
    this.name="ApiError";
    this.status=status;
    this.body=body;
  }
}

async function requestJson<T>(path:string, init:RequestInit):Promise<T>{
  if(!apiBaseUrl) throw new Error("API_BASE_URL is not configured");
  const response=await fetch(`${apiBaseUrl}${path}`,{...init,cache:"no-store",headers:{"Content-Type":"application/json",...init.headers}});
  if(!response.ok){
    let body:unknown;
    try{body=await response.json()}catch{body=undefined}
    throw new ApiError(response.status,body);
  }
  return response.json() as Promise<T>;
}

export async function getJson<T>(path:string):Promise<T>{
  if(!apiBaseUrl) throw new Error("API_BASE_URL is not configured");
  const response=await fetch(`${apiBaseUrl}${path}`,{cache:"no-store"});
  if(!response.ok){let body:unknown;try{body=await response.json()}catch{body=undefined}throw new ApiError(response.status,body)}
  return response.json() as Promise<T>;
}
export const postJson=<T>(path:string,body:unknown)=>requestJson<T>(path,{method:"POST",body:JSON.stringify(body)});
export const patchJson=<T>(path:string,body:unknown)=>requestJson<T>(path,{method:"PATCH",body:JSON.stringify(body)});
export async function deleteRequest(path:string):Promise<void>{
  if(!apiBaseUrl) throw new Error("API_BASE_URL is not configured");
  const response=await fetch(`${apiBaseUrl}${path}`,{method:"DELETE",cache:"no-store"});
  if(!response.ok){let body:unknown;try{body=await response.json()}catch{body=undefined}throw new ApiError(response.status,body)}
}
export const queryString=(values:Record<string,string|number|undefined>)=>{
 const params=new URLSearchParams(); Object.entries(values).forEach(([key,value])=>{if(value!==undefined&&value!=="")params.set(key,String(value))}); return params.toString();
};
