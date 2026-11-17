import {createRouter,createWebHashHistory} from 'vue-router';
import CoursesView from './pages/CoursesView.vue';
import DocumentsView from './pages/DocumentsView.vue';
import ChatView from './pages/ChatView.vue';
import RetrievalView from './pages/RetrievalView.vue';
export default createRouter({history:createWebHashHistory(),routes:[{path:'/',component:CoursesView},{path:'/documents',component:DocumentsView},{path:'/chat',component:ChatView},{path:'/retrieval',component:RetrievalView}]});
