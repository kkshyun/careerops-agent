import assert from "node:assert/strict";
import {describe,it} from "node:test";
import {ApiError} from "../api/client.ts";
import {actionError,errorMessage} from "./result.ts";

describe("action error mapping",()=>{
  it("maps known HTTP statuses to stable Korean messages",()=>{
    assert.equal(errorMessage(400),"입력값을 확인해주세요.");
    assert.equal(errorMessage(404),"대상을 찾을 수 없습니다. 새로고침 후 다시 시도해주세요.");
    assert.equal(errorMessage(409,"중복입니다."),"중복입니다.");
  });
  it("preserves the API status in an error result",()=>{
    assert.deepEqual(actionError(new ApiError(404,undefined)),{ok:false,kind:"error",status:404,message:"대상을 찾을 수 없습니다. 새로고침 후 다시 시도해주세요."});
  });
});
