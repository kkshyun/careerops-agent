import type { Page } from "@/lib/types";

export const apiBaseUrl = process.env.API_BASE_URL?.replace(/\/$/, "");
export const fixturePage = <T>(all:T[], page=0, size=20):Page<T> => ({content:all.slice(page*size,(page+1)*size),totalElements:all.length,totalPages:Math.ceil(all.length/size),page,size});
export async function getJson<T>(path:string):Promise<T>{
  if(!apiBaseUrl) throw new Error("API_BASE_URL is not configured");
  const response=await fetch(`${apiBaseUrl}${path}`,{cache:"no-store"});
  if(!response.ok) throw new Error(`Backend request failed (${response.status}): ${path}`);
  return response.json() as Promise<T>;
}
export const queryString=(values:Record<string,string|number|undefined>)=>{
 const params=new URLSearchParams(); Object.entries(values).forEach(([key,value])=>{if(value!==undefined&&value!=="")params.set(key,String(value))}); return params.toString();
};
