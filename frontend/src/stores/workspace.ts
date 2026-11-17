import {defineStore} from 'pinia';
export const useWorkspace=defineStore('workspace',{state:()=>({knowledgeBaseId:null as string|null,documentIds:[] as string[],conversationId:null as string|null,activeTurnId:null as string|null})});
