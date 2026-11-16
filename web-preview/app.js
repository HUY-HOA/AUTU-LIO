(() => {
"use strict";
const $ = (id) => document.getElementById(id);
const esc = (value) => String(value == null ? "" : value).replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const roles = [
  {id:"OWNER",title:"Chủ sở hữu",sub:"Quản trị toàn bộ AUTU-LIO",icon:"♙"},
  {id:"DEVELOPER",title:"Nhà phát triển",sub:"Soạn thảo kịch bản theo quyền được cấp",icon:"⌘"},
  {id:"DESIGNER",title:"Nhà thiết kế",sub:"Thiết kế bố cục và màu sắc",icon:"◈"},
  {id:"REVIEWER",title:"Người kiểm duyệt",sub:"Kiểm tra và đánh giá bản gửi",icon:"✓"},
  {id:"CUSTOMER",title:"Người dùng",sub:"Sử dụng các AUTU đã được phát hành",icon:"♧"}
];
const adminTabs = [
  ["overview","⌂","Tổng quan"],["scripts","☷","Kịch bản"],["customers","♙","Khách hàng"],["devices","▣","Thiết bị"],["more","•••","Thêm"]
];
const customerTabs = [
  ["home","⌂","Trang chủ"],["scripts","☷","Kịch bản"],["device","▣","Thiết bị"],["settings","⚙","Cài đặt"]
];
const state = {
  role:null, page:"", adminSub:"", previewChooser:false, theme:"dark", scriptFilter:"all", scriptQuery:"", inviteRole:"DEVELOPER", invites:[],
  libraryQuery:"", libraryCategory:"all", editorName:"", editorSource:"", drafts:[], modal:"", accent:"blue",
  runnerVisible:false, runnerState:"IDLE", runnerCollapsed:false, runnerEditorOpen:false, runnerVariables:[], runnerPos:null,
  reviewerProject:"", statusMessage:""
};
const templates = [{"id":"single_tap","name":"Chạm một lần","description":"Chạm tại một tọa độ trên màn hình.","category":"actions","source":"// Thay tọa độ X và Y theo ứng dụng mục tiêu\nCHẠM(200, 400)"},{"id":"tap_sequence","name":"Chuỗi chạm có thời gian chờ","description":"Chạm vị trí thứ nhất, chờ 500 ms rồi chạm vị trí thứ hai.","category":"actions","source":"CHẠM(200, 400)\nCHỜ(500)\nCHẠM(500, 400)"},{"id":"long_press","name":"Nhấn giữ","description":"Nhấn giữ tại một vị trí trong 800 ms.","category":"actions","source":"NHẤN_GIỮ(200, 400, 800)"},{"id":"swipe_up","name":"Vuốt lên","description":"Vuốt từ dưới lên trên trong 450 ms.","category":"actions","source":"VUỐT(360, 850, 360, 350, 450)"},{"id":"repeat_tap","name":"Lặp thao tác chạm","description":"Lặp thao tác chạm theo số lần đã đặt.","category":"logic","source":"ĐẶT soLan = 3\nLẶP(soLan) {\n    CHẠM(200, 400)\n    CHỜ(500)\n}"},{"id":"if_battery","name":"Kiểm tra pin trước khi chạy","description":"Chỉ tiếp tục thao tác khi pin còn trên 20%.","category":"logic","source":"NẾU (PIN() > 20) {\n    CHẠM(200, 400)\n} NGƯỢC_LẠI {\n    CHỜ(1000)\n}"},{"id":"open_app","name":"Mở ứng dụng","description":"Mở ứng dụng bằng tên gói Android hợp lệ.","category":"actions","source":"MỞ_ỨNG_DỤNG(\"com.example.game\")"},{"id":"while_loop","name":"Lặp theo điều kiện","description":"Tăng biến đếm và dừng lặp khi đạt điều kiện.","category":"logic","source":"ĐẶT dem = 0\nLẶP_KHI (dem < 5) {\n    dem = dem + 1\n    CHẠM(200, 400)\n    CHỜ(300)\n}"},{"id":"wait_until_condition","name":"Chờ ứng dụng hoặc điều kiện sẵn sàng","description":"Kiểm tra điều kiện định kỳ và tự dừng khi hết thời hạn.","category":"logic","source":"CHỜ_ĐẾN_KHI(ỨNG_DỤNG_ĐANG_MỞ(\"com.example.game\"), 15000, 250)\nCHẠM(200, 400)"},{"id":"until_loop","name":"Lặp đến khi đạt mục tiêu","description":"Thực hiện thao tác cho đến khi biến đạt điều kiện dừng.","category":"logic","source":"ĐẶT dem = 0\nLẶP_ĐẾN_KHI(dem >= 5) {\n    dem = dem + 1\n    CHẠM(200, 400)\n    CHỜ(300)\n}"},{"id":"go_back","name":"Quay lại","description":"Thực hiện thao tác Quay lại của Android.","category":"system","source":"QUAY_LẠI()"},{"id":"go_home","name":"Về màn hình chính","description":"Đưa thiết bị về màn hình chính Android.","category":"system","source":"VỀ_MÀN_HÌNH_CHÍNH()"},{"id":"show_recents","name":"Mở ứng dụng gần đây","description":"Mở giao diện ứng dụng gần đây của Android.","category":"system","source":"ỨNG_DỤNG_GẦN_ĐÂY()"},{"id":"show_notifications","name":"Mở thông báo","description":"Mở ngăn thông báo Android.","category":"system","source":"MỞ_THÔNG_BÁO()"},{"id":"show_quick_settings","name":"Mở cài đặt nhanh","description":"Mở bảng cài đặt nhanh Android.","category":"system","source":"MỞ_CÀI_ĐẶT_NHANH()"},{"id":"show_power_dialog","name":"Hiện menu nguồn","description":"Mở menu nguồn bằng thao tác hệ thống Android.","category":"system","source":"HIỆN_MENU_NGUỒN()"},{"id":"lock_screen","name":"Khóa màn hình","description":"Khóa màn hình nếu phiên bản Android hỗ trợ.","category":"system","source":"KHÓA_MÀN_HÌNH()"},{"id":"split_screen","name":"Chia đôi màn hình","description":"Bật chế độ chia đôi nếu Android cung cấp thao tác này.","category":"system","source":"CHIA_MÀN_HÌNH()"}];
function toast(message){
  const node=$("toast"); if(!node)return;
  node.textContent=message; node.classList.add("show");
  clearTimeout(state.toastTimer); state.toastTimer=setTimeout(()=>node.classList.remove("show"),2600);
}
function brandHeader(admin){
  return '<div class="brand-header"><div class="brand-mark">A</div><div><b>AUTU-LIO</b><small>'+(admin?"Quản trị và phát triển AUTU":"Tự động hóa thao tác")+'</small></div></div>';
}
function heading(title,sub){
  return '<div class="heading"><h1>'+esc(title)+'</h1><p>'+esc(sub||"")+'</p></div>';
}
function card(title,body,extra){
  return '<section class="card">'+(title?'<div class="card-title">'+title+'</div>':"")+(body||"")+(extra||"")+'</section>';
}
function notice(title,body,kind){
  return '<section class="notice '+(kind||"")+'"><b>'+esc(title)+'</b><p>'+esc(body)+'</p></section>';
}
function empty(icon,title,body){
  return '<div class="empty"><div class="empty-icon">'+icon+'</div><b>'+esc(title)+'</b><p>'+esc(body)+'</p></div>';
}
function button(label,action,kind,disabled){
  return '<button class="btn '+(kind||"outline")+'" data-action="'+esc(action)+'" '+(disabled?"disabled":"")+'>'+label+'</button>';
}
function menuItem(icon,title,sub,page){
  return '<button class="menu-item" data-page="'+esc(page)+'"><span class="mi-icon">'+icon+'</span><span class="grow"><b>'+esc(title)+'</b><small>'+esc(sub)+'</small></span><span class="chevron">›</span></button>';
}
function authScreen(){
  return '<div class="chooser"><div class="chooser-card">'+brandHeader(false)+heading("Đăng nhập","Đăng nhập để xác minh quyền tài khoản.")+
    button("Đăng nhập bằng Google","sign-in","primary",true)+
    notice("Đăng nhập chưa được bật","Cần cấu hình Auth API URL và Google Server Client ID trước khi sử dụng.","warn")+
    card("Xác minh vai trò",'<p>AUTU-LIO không tự cấp quyền Admin. Vai trò được máy chủ xác minh sau khi Google xác thực tài khoản.</p>')+
    button("Xem thử 5 giao diện theo vai trò","open-role-chooser","outline")+
    '</div></div>';
}
function chooser(){
  return '<div class="chooser"><div class="chooser-card">'+brandHeader(false)+heading("Chọn giao diện kiểm thử","Bộ chọn vai trò này chỉ có trong bản Debug; bản Release mở màn hình đăng nhập.")+
    notice("Chế độ xem thử dành cho bản Debug","Các lựa chọn dưới đây chỉ kiểm tra bố cục. Chúng không đăng nhập và không cấp quyền thật.","info")+
    button("← Màn hình đăng nhập","back-auth","outline")+
    '<div class="role-list">'+roles.map(r=>'<button class="role-card" data-role="'+r.id+'"><span class="role-icon">'+r.icon+'</span><span class="grow"><b>'+r.title+'</b><small>'+r.sub+'</small></span><span class="chevron">›</span></button>').join("")+'</div></div></div>';
}
function header(){
 const role=roles.find(r=>r.id===state.role);
 return '<header class="topbar"><div class="brand"><div class="brand-mark">A</div><div><div class="brand-name">AUTU-LIO</div><div class="brand-sub">'+esc(role?role.title:"Bản xem trước")+'</div></div></div><span class="preview-label">WEB PREVIEW</span><button class="small-link" data-action="open-android-map">Đối chiếu Android</button><button class="small-link" data-action="back-roles">Đổi vai trò xem thử</button></header>';
}
function androidMap(){
 const groups=[
  ["Chủ sở hữu",[
   ["Tổng quan","MainActivity.kt · OverviewScreen","Có giao diện; số liệu thật cần phiên OWNER và API"],
   ["AUTU / Kịch bản","MainActivity.kt · ScriptsScreen","Danh sách, tìm kiếm, lọc, tạo và mở bản nháp"],
   ["Trình soạn thảo","MainActivity.kt · ScriptEditorActivity","Có editor riêng, thư viện mẫu, kiểm tra mã, lưu nháp và chuẩn bị chạy"],
   ["Khách hàng","LiveManagementScreens.kt","Màn hình quản lý; dữ liệu cần API và phiên OWNER"],
   ["Thiết bị","LiveManagementScreens.kt","Màn hình thiết bị; dữ liệu cần API và phiên OWNER"],
   ["Cộng tác viên","CollaboratorsScreen.kt · CollaboratorsServerScreen.kt","Có luồng bản nháp cục bộ và luồng máy chủ"],
   ["Dự án cộng tác","SharedProjectScreens.kt","Có workspace dự án và phân công; cần API hoạt động"],
   ["Giấy phép / Gói AUTU","LiveManagementScreens.kt","Màn hình quản lý; cần dữ liệu máy chủ"],
   ["Thư viện mẫu","AdminTemplateLibraryScreen.kt","Có thư viện mẫu trong Android"],
   ["Phát hành / Nhật ký / Cài đặt","MainActivity.kt · AdminAuditLogsScreen.kt","Có các màn hình quản trị; thao tác máy chủ tùy cấu hình"]
  ]],
  ["Nhà phát triển",[
   ["Không gian nhà phát triển","CollaboratorWorkspace.kt · DeveloperWorkspace","Bản nháp riêng, mã hóa cục bộ; quyền thật do máy chủ cấp"],
   ["Trình soạn thảo riêng","MainActivity.kt · ScriptEditorActivity","Mở editor Android riêng; Web chỉ mô phỏng bố cục"],
   ["Dự án cộng tác","SharedProjectScreens.kt","Dự án được giao và bản nháp dùng chung theo revision"]
  ]],
  ["Nhà thiết kế",[
   ["Không gian thiết kế","CollaboratorWorkspace.kt · DesignerWorkspace","Chỉnh nội dung/màu và xem trước UI; không sửa mã kịch bản"],
   ["Dự án cộng tác","SharedProjectScreens.kt","Dự án được giao theo quyền"]
  ]],
  ["Người kiểm duyệt",[
   ["Hàng đợi kiểm duyệt","CollaboratorWorkspace.kt · ReviewerWorkspace","Xem bản gửi, quyền kiểm thử và duyệt/từ chối theo quyền"],
   ["Dự án cộng tác","SharedProjectScreens.kt","Danh sách dự án được giao"]
  ]],
  ["Người dùng",[
   ["Trang chủ / trạng thái giấy phép","MainActivity.kt · CustomerHome","Thông tin giấy phép cần tài khoản và API"],
   ["Kho AUTU đã phát hành","MainActivity.kt · CustomerPackagesScreen","Tải gói theo giấy phép và thiết bị đã xác minh"],
   ["Thiết bị và giấy phép","LiveManagementScreens.kt · CustomerDeviceLicenseScreen","Có luồng đăng ký thiết bị RSA-3072 và gỡ thiết bị; cần máy chủ"],
   ["Cài đặt / mã lời mời / đăng xuất","MainActivity.kt · SettingsScreen","Nhập mã mời và quản lý phiên"]
  ]],
  ["Chức năng nền tảng",[
   ["Đăng nhập và khôi phục phiên","auth/AuthenticatedAppHost.kt · AuthRepository.kt","Release dùng Google Sign-In; cần cấu hình OAuth và Worker"],
   ["Ngôn ngữ AUTU-LIO Script","script/ScriptCompiler.kt","Có parser/AST, chẩn đoán lỗi và kiểm tra giới hạn"],
   ["Bộ thực thi","runtime/ScriptExecutionEngine.kt","Có trạng thái chạy/tạm dừng/tiếp tục/dừng; cần kiểm thử thiết bị"],
   ["Cửa sổ chạy nổi và thao tác Android","accessibility/AutoClickAccessibilityService.kt","Có AccessibilityService, cử chỉ và nút nổi; chưa xác nhận trên điện thoại thật"]
  ]]
 ];
 const rows=groups.map(group=>'<section class="card"><div class="card-title">'+esc(group[0])+'</div>'+group[1].map(item=>'<div class="map-row"><b>'+esc(item[0])+'</b><small>'+esc(item[1])+'</small><p>'+esc(item[2])+'</p></div>').join("")+'</section>').join("");
 return brandHeader(false)+button("← Quay lại màn hình trước","back-android-map","outline")+heading("Đối chiếu Android","Danh mục màn hình và mã nguồn Android hiện tại trên nhánh main.")+
 notice("Phân biệt trạng thái","Có mã nguồn không đồng nghĩa đã kết nối máy chủ hoặc kiểm thử thành công trên điện thoại. Bản Web này chỉ phản chiếu giao diện, không chạy mã Android.","warn")+
 rows+card("Quy tắc cập nhật","<p>Khi thêm hoặc đổi màn hình Android, phải cập nhật danh mục này và màn hình Web tương ứng. Mọi mục chưa có dữ liệu thật phải hiển thị trạng thái chờ cấu hình, không tự tạo dữ liệu giả.</p>");
}
function navBar(){
 const tabs=state.role==="OWNER"?adminTabs:state.role==="CUSTOMER"?customerTabs:[];
 if(!tabs.length)return "";
 return '<nav class="bottom-nav">'+tabs.map(t=>'<button class="nav-item '+(state.page===t[0]||(state.page==="editor"&&t[0]==="scripts")?"active":"")+'" data-page="'+t[0]+'"><span>'+t[1]+'</span><small>'+t[2]+'</small></button>').join("")+'</nav>';
}
function adminOverview(){
 return brandHeader(true)+heading("Tổng quan","Số liệu quản lý được đồng bộ trực tiếp từ máy chủ")+
  notice("Chưa có phiên đăng nhập thật","Số liệu quản lý sẽ được tải sau khi đăng nhập bằng tài khoản chủ sở hữu.","warn")+
  '<div class="section-title">Truy cập nhanh</div>'+
  '<div class="quick-grid">'+
  '<button class="quick-tile" data-action="create-script"><span>＋</span><b>Tạo AUTU</b><small>Tạo kịch bản mới</small></button>'+
  '<button class="quick-tile" data-page="scripts"><span>☷</span><b>Kịch bản</b><small>Mở danh sách kịch bản</small></button>'+
  '<button class="quick-tile" data-page="customers"><span>♙</span><b>Khách hàng</b><small>Quản lý tài khoản</small></button>'+
  '<button class="quick-tile" data-page="devices"><span>▣</span><b>Thiết bị</b><small>Thiết bị đã đăng ký</small></button>'+
  '<button class="quick-tile" data-page="more" data-sub="licenses"><span>◇</span><b>Giấy phép</b><small>Cấp và thu hồi quyền</small></button>'+
  '<button class="quick-tile" data-page="more" data-sub="packages"><span>⇧</span><b>Phát hành gói</b><small>Quản lý gói AUTU</small></button>'+
  '</div>';
}
function adminScripts(){
 const filtered=state.drafts.filter(d=>d.name.toLowerCase().includes(state.scriptQuery.toLowerCase())&&(state.scriptFilter==="all"||state.scriptFilter==="draft"));
 return brandHeader(true)+'<div class="heading-row">'+heading("AUTU","Quản lý và xây dựng các AUTU của bạn")+'<div class="heading-buttons">'+button("Quyền","go-collaborators","outline")+button("＋","create-script","primary")+'</div></div>'+
  '<div class="field"><input id="script-search" placeholder="⌕  Tìm kiếm kịch bản" value="'+esc(state.scriptQuery)+'"></div>'+
  '<div class="filter-row">'+[["all","Tất cả"],["draft","Bản nháp"],["published","Đã phát hành"]].map(f=>'<button class="filter '+(state.scriptFilter===f[0]?"active":"")+'" data-filter="'+f[0]+'">'+f[1]+'</button>').join("")+'</div>'+
  '<p class="muted">Trên Android, bản nháp được lưu trên thiết bị và chưa đồng bộ máy chủ. Bản Web chỉ mô phỏng màn hình.</p>'+
  (filtered.length?filtered.map((d,i)=>card('<div class="row between"><span>'+esc(d.name)+'</span><span class="status warn">Bản nháp</span></div>','<p>Chạm để mở trình soạn thảo và tiếp tục chỉnh sửa AUTU.</p>'+button("Mở trình soạn thảo","open-draft:"+i,"soft"))).join(""):empty("☷",state.scriptFilter==="published"?"Chưa có kịch bản phát hành":"Chưa có kịch bản nào",state.scriptFilter==="published"?"Các kịch bản đã phát hành sẽ xuất hiện tại đây sau khi được kết nối.":"Tạo kịch bản đầu tiên để bắt đầu."));
}
function createScriptDialog(){
 state.modal="create";
 renderModal();
}
function customerList(){
 return brandHeader(true)+heading("Khách hàng","Tài khoản thật đã đăng nhập vào AUTU-LIO")+
  notice("Chưa có phiên đăng nhập thật","Danh sách tài khoản khách hàng chỉ được tải sau khi đăng nhập OWNER và kết nối máy chủ.","warn")+
  empty("♙","Chưa có khách hàng","Tài khoản sẽ xuất hiện sau khi người dùng đăng nhập bằng Google.");
}
function adminDevices(){
 return brandHeader(true)+heading("Thiết bị","Thiết bị đăng ký bằng khóa bảo mật riêng")+
  notice("Chưa có dữ liệu thiết bị thật","Thiết bị chỉ xuất hiện sau khi khách hàng đăng nhập và chủ động đăng ký thiết bị.","warn")+
  empty("▣","Chưa có thiết bị","Không hiển thị IMEI, số sê-ri, MAC hoặc GPS để nhận diện thiết bị.");
}
function adminMore(){
 const items=[
 ["♙","Cộng tác viên","Mời và phân quyền nhà phát triển, nhà thiết kế, người kiểm duyệt","collaborators"],
 ["▤","Dự án cộng tác","Quản lý dự án và thành viên được giao","projects"],
 ["◇","Giấy phép","Cấp, gia hạn và thu hồi quyền sử dụng","licenses"],
 ["☷","Thư viện mẫu AUTU","Quản lý hàm và thành phần dùng chung","library"],
 ["⌘","Gói AUTU","Mã hóa, phát hành phiên bản và phân phối AUTU cho khách hàng","packages"],
 ["▶","Phát hành","Quản lý phiên bản và phân phối","release"],
 ["ⓘ","Nhật ký","Theo dõi sự kiện và lỗi hệ thống","logs"],
 ["⚙","Cài đặt","Tùy chỉnh ứng dụng quản trị","settings"]
 ];
 return brandHeader(true)+heading("Quản lý khác","Công cụ quản trị AUTU-LIO")+'<div class="menu-list">'+items.map(x=>menuItem(x[0],x[1],x[2],x[3])).join("")+'</div>'+button("◐  Chuyển giao diện sáng / tối","theme","outline");
}
function libraryFiltered(){
 const query=state.libraryQuery.trim().toLowerCase();
 return templates.filter(t=>(state.libraryCategory==="all"||t.category===state.libraryCategory)&&
  (!query||[t.name,t.description,t.source].join(" ").toLowerCase().includes(query)));
}
function libraryCountLabel(){return "Đang hiển thị "+libraryFiltered().length+"/"+templates.length+" mẫu";}
function libraryCards(){
 const items=libraryFiltered();
 if(!items.length)return empty("⌕","Không tìm thấy mẫu phù hợp","Thử từ khóa hoặc nhóm khác.");
 return items.map(t=>{
  const index=templates.findIndex(item=>item.id===t.id);
  return '<section class="template-card"><div class="row between"><b>'+esc(t.name)+'</b><span class="status ok">Script 0.3</span></div><p>'+esc(t.description)+'</p><pre>'+esc(t.source)+'</pre>'+button("Mở và chỉnh sửa mẫu","open-template:"+index,"primary")+'</section>';
 }).join("");
}
function nestedAdmin(){
 const names={
 collaborators:["Cộng tác viên","Mời và phân quyền nhà phát triển, nhà thiết kế, người kiểm duyệt"],
 projects:["Dự án cộng tác","Quản lý dự án và thành viên được giao"],
 licenses:["Giấy phép","Cấp, gia hạn và thu hồi quyền sử dụng"],
 library:["Thư viện mẫu AUTU","Quản lý hàm và thành phần dùng chung"],
 packages:["Gói AUTU","Mã hóa, phát hành phiên bản và phân phối AUTU cho khách hàng"],
 release:["Phát hành","Quản lý phiên bản và phân phối"],
 logs:["Nhật ký","Theo dõi sự kiện và lỗi hệ thống"],
 settings:["Cài đặt","Tùy chỉnh ứng dụng quản trị"]
 };
 const n=names[state.adminSub]||["Quản lý","Công cụ quản trị AUTU-LIO"];
 let body="";
 if(state.adminSub==="collaborators"){
   body=card("Bản nháp trên thiết bị",'<p>Các Gmail bên dưới chỉ được lưu thành bản nháp trên điện thoại. Chưa gửi lời mời và chưa cấp quyền cho bất kỳ ai vì máy chủ chưa kết nối.</p>')+
   '<div class="field"><label>Địa chỉ Gmail</label><input id="invite-email" placeholder="name@gmail.com"></div>'+
   '<div class="section-title">Chọn vai trò</div>'+
   '<div class="role-drafts">'+[
     ["DEVELOPER","Nhà phát triển","Viết, sửa và kiểm thử mã kịch bản; gửi để duyệt"],
     ["DESIGNER","Nhà thiết kế","Thiết kế giao diện, tài nguyên và xem trước"],
     ["REVIEWER","Người kiểm duyệt","Kiểm tra, duyệt hoặc từ chối bản phát hành"]
   ].map(x=>'<button class="role-draft '+((state.inviteRole||"DEVELOPER")===x[0]?"selected":"")+'" data-invite-role="'+x[0]+'"><span class="radio-dot"></span><span><b>'+x[1]+'</b><small>'+x[2]+'</small></span></button>').join("")+'</div>'+
   button("＋  Thêm bản nháp lời mời","invite-draft","primary")+
   '<div class="section-title">Danh sách cộng tác viên dự kiến ('+(state.invites||[]).length+')</div>'+
   ((state.invites||[]).length?(state.invites||[]).map((x,i)=>card('<div class="row between"><b>'+esc(x.email)+'</b><span class="status warn">Chưa gửi</span></div>','<p>'+esc(x.role)+'</p>'+button("Xóa","remove-invite:"+i,"outline"))).join(""):'<p class="muted">Chưa có bản nháp. Thêm Gmail ở trên để chuẩn bị phân quyền.</p>');
 }else if(state.adminSub==="projects"){
   body=notice("Chưa có phiên đăng nhập","Đăng nhập lại để quản lý dự án cộng tác trên máy chủ.","warn")+empty("▤","Chưa có dự án","Dự án và thành viên chỉ xuất hiện sau khi dịch vụ xác thực và API được kết nối.");
 }else if(state.adminSub==="projects"){
   body=notice("Chưa có phiên đăng nhập","Đăng nhập lại để quản lý dự án cộng tác.","warn")+
    empty("▤","Chưa có dự án","Đăng nhập lại để tải dự án và thành viên từ máy chủ.");
 }else if(state.adminSub==="licenses"){
   body=card("Thông tin giấy phép",'<p>Khách hàng · Gói sử dụng · Thời hạn · Giới hạn thiết bị · Trạng thái</p>')+empty("◇","Chưa có giấy phép","Dữ liệu giấy phép sẽ xuất hiện sau khi kết nối máy chủ.");
 }else if(state.adminSub==="library"){
   body=card("Tương thích AUTU Script 0.3",'<p>18 mẫu lệnh có thể mở trực tiếp trong trình soạn thảo. Mỗi mẫu được kiểm tra cú pháp trước khi đưa vào thư viện Android.</p>')+
    '<div class="field"><input id="library-search" placeholder="Tìm theo tên, mô tả hoặc lệnh" value="'+esc(state.libraryQuery)+'"></div>'+
    '<div class="filter-row">'+[["all","Tất cả"],["actions","Thao tác"],["logic","Điều kiện và vòng lặp"],["system","Android"]].map(x=>'<button class="filter '+(state.libraryCategory===x[0]?"active":"")+'" data-category="'+x[0]+'">'+x[1]+'</button>').join("")+'</div>'+
    '<p class="muted" id="library-count">'+libraryCountLabel()+'</p>'+
    '<div id="library-results">'+libraryCards()+'</div>';
 }else if(state.adminSub==="packages"){
   body=notice("Chưa có phiên đăng nhập thật","Gói AUTU được tải và quản lý qua API sau khi xác thực chủ sở hữu.","warn")+empty("⌘","Chưa có gói AUTU","Chưa có package được phát hành hoặc phân phối.");
 }else if(state.adminSub==="release"){
   body=card("Quy trình phát hành",'<p>Kiểm tra · Tăng phiên bản · Ghi chú · Chọn kênh · Xác nhận · Phân phối</p>')+empty("▶","Chưa có bản phát hành","Chỉ phát hành khi gói đã được kiểm tra và ký xác thực.");
 }else if(state.adminSub==="logs"){
   body=notice("Cần đăng nhập OWNER","Nhật ký hệ thống yêu cầu tài khoản chủ sở hữu và máy chủ hoạt động.","warn")+empty("ⓘ","Chưa có nhật ký","Sự kiện hệ thống sẽ hiển thị sau khi Worker được kết nối.");
 }else if(state.adminSub==="settings"){
   body=menuItem("◐","Giao diện","Chuyển đổi sáng và tối","theme")+menuItem("ⓘ","Thông tin ứng dụng","AUTU-LIO · 0.1.0","about")+menuItem("▣","Bảo mật","Vai trò và quyền được xác minh từ máy chủ.","security")+menuItem("⇥","Đăng xuất","Kết thúc phiên đăng nhập trên thiết bị này.","signout");
 }
 return brandHeader(true)+button("← Quay lại","more","outline") + heading(n[0],n[1])+body+
  (["packages","logs"].includes(state.adminSub)?"":button("Kiểm tra kết nối máy chủ","server-check","outline"));
}
function customerHome(){
 return brandHeader(false)+heading("Xin chào!","Trung tâm sử dụng AUTU-LIO")+
  card("Trạng thái giấy phép",'<div class="row"><span class="icon-box">◇</span><div class="grow"><b class="danger-text">Chưa được cấp giấy phép.</b><p>Đây là dữ liệu xem trước, chưa xác minh tài khoản bằng máy chủ.</p></div></div>')+
  '<div class="section-title">Gói AUTU của bạn</div><p class="muted">Chỉ các gói đã phát hành, có giấy phép hợp lệ và được cấp cho thiết bị này mới có thể tải và chạy.</p>'+
  button("Mở kho AUTU","scripts","primary")+
  button("Thiết bị và giấy phép","device","outline")+
  '<p class="muted center">Tài khoản xem thử</p>';
}
function customerScripts(){
 return brandHeader(false)+heading("Kho AUTU đã phát hành","Gói được máy chủ xác minh giấy phép và mã hóa riêng cho thiết bị này.")+
  notice("Chưa có phiên đăng nhập thật","Chức năng này cần máy chủ và tài khoản đã được xác minh. Chế độ xem trước không hiển thị dữ liệu mẫu giả.","warn");
}
function customerDevice(){
 return brandHeader(false)+heading("Thiết bị và giấy phép","Thông tin được xác minh trực tiếp từ máy chủ")+
  notice("Chưa có phiên đăng nhập thật","Chức năng này cần máy chủ và tài khoản đã được xác minh. Chế độ xem trước không hiển thị dữ liệu mẫu giả.","warn");
}
function customerSettings(){
 return brandHeader(false)+heading("Cài đặt","Tùy chỉnh AUTU-LIO")+
  menuItem("◐","Giao diện","Chuyển đổi sáng và tối","theme")+
  menuItem("ⓘ","Thông tin ứng dụng","AUTU-LIO v0.1.0","about")+
  menuItem("▣","Quyền riêng tư","Vai trò và giấy phép được máy chủ xác minh.","privacy")+
  card("Bạn có mã lời mời cộng tác viên?",'<p>Đăng nhập bằng đúng Gmail đã nhận lời mời, sau đó nhập mã bên dưới để chấp nhận và nhận vai trò.</p><div class="field"><label>Mã lời mời</label><textarea id="invite-token" rows="2" placeholder="Nhập mã lời mời"></textarea></div>'+button("Chấp nhận lời mời","accept-invite","primary",true))+
  menuItem("⇥","Đăng xuất","Kết thúc phiên đăng nhập trên thiết bị này.","signout");
}
function developerWorkspace(){
 return '<div class="workspace-top"><div><b>Không gian nhà phát triển</b><small>Tài khoản mẫu · Bản xem trước</small></div>'+button("Đăng xuất","signout","outline")+'</div>'+
  heading("Kịch bản của nhà phát triển","Mã nguồn được chỉnh sửa trong cửa sổ trình soạn thảo riêng.")+
  button("▤  Mở dự án cộng tác","collaborator-projects","outline")+
  '<div class="field"><label>Tên kịch bản</label><input id="developer-script-name" placeholder="Tên kịch bản"></div>'+
  '<div class="section-title">Bản nháp đã lưu</div>'+empty("⌘","Chưa có bản nháp","Nhập tên rồi mở trình soạn thảo riêng để tạo kịch bản.")+
  button("✎  Mở trình soạn thảo riêng","developer-editor","primary")+
  notice("Lưu trữ bản nháp","Trên Android, bản nháp nhà phát triển được mã hóa và lưu riêng với kho bản nháp của Chủ sở hữu. Bản Web không truy cập kho đó; dữ liệu xem trước không đồng bộ hoặc gửi duyệt.","info")+
  card("Quyền mặc định theo mã Android",'<p>Viết và sửa mã kịch bản · Kiểm thử kịch bản · Gửi bản để duyệt</p><p class="muted">Quyền tài khoản thật do máy chủ cấp riêng và Chủ sở hữu có thể tắt.</p>');
}
function designerWorkspace(){
 return '<div class="workspace-top"><div><b>Không gian nhà thiết kế</b><small>Tài khoản mẫu · Bản xem trước</small></div>'+button("Đăng xuất","signout","outline")+'</div>'+
  heading("Bản xem trước giao diện","Thiết kế bố cục và màu sắc trong khu vực được giao.")+
  button("▤  Mở dự án thiết kế","collaborator-projects","outline")+
  '<section class="designer-preview" id="designer-preview"><div class="row"><span class="icon-box" id="designer-icon">✣</span><div><b>AUTU-LIO</b><p class="muted">Tự động hóa thao tác</p></div></div><div class="section-title">Kịch bản của bạn</div><div class="sample-row"><b>Kịch bản mẫu</b><small>Bản xem trước — chưa phát hành</small></div><button class="btn primary" disabled>Nút xem trước</button></section>'+
  '<div class="section-title">Chọn màu nhấn</div><div class="actions three">'+
  '<button class="filter active" data-color="blue">● Blue</button><button class="filter" data-color="green">● Green</button><button class="filter" data-color="violet">● Violet</button></div>'+
  notice("Bản xem trước cục bộ","Thay đổi hiện chỉ là bản xem trước cục bộ. Vai trò nhà thiết kế không được xem hoặc sửa mã kịch bản, giấy phép hay phát hành.","info")+
  card("Quyền mặc định theo mã Android",'<p>Thiết kế giao diện · Xem trước giao diện · Gửi bản để duyệt</p><p class="muted">Quyền tài khoản thật do máy chủ cấp riêng và Chủ sở hữu có thể tắt.</p>');
}
function reviewerWorkspace(){
 return '<div class="workspace-top"><div><b>Không gian kiểm duyệt</b><small>Tài khoản mẫu · Bản xem trước</small></div>'+button("Đăng xuất","signout","outline")+'</div>'+
  heading("Kiểm tra bản gửi duyệt","Bạn chỉ xem và đánh giá các bản được gửi vào hàng chờ.")+
  button("▤  Mở hàng đợi dự án","collaborator-projects","outline")+
  notice("Chưa có bản phát hành nào được gửi duyệt","Khi kết nối máy chủ, các bản gửi duyệt sẽ xuất hiện ở đây.","warn")+
  card("Quyền kiểm duyệt",'<p>Xem mã được giao · Chạy kiểm thử · Duyệt hoặc từ chối đề xuất</p><p>Không có quyền phát hành cuối cùng hoặc quản lý tài khoản.</p>')+
  card("Quyền mặc định theo mã Android",'<p>Kiểm thử kịch bản · Duyệt hoặc từ chối bản gửi</p><p class="muted">Quyền tài khoản thật do máy chủ cấp riêng và Chủ sở hữu có thể tắt.</p>');
}
function collaboratorProjects(){
 return '<div class="workspace-top"><div><b>Dự án cộng tác</b><small>Không gian dự án được giao</small></div>'+button("← Quay lại","back-workspace","outline")+'</div>'+
  heading("Dự án cộng tác","Các dự án và bản nháp dùng chung theo quyền tài khoản.")+
  notice("Chưa có phiên đăng nhập thật","Bản xem trước Web không kết nối danh tính hoặc API dự án của tài khoản Android.","warn")+
  empty("▤","Chưa có dự án được giao","Dự án sẽ xuất hiện sau khi đăng nhập và chủ sở hữu phân công.");
}
function editorScreen(){
 return brandHeader(true)+button("← Quay lại","editor-back","outline")+
  heading(state.editorName||"Kịch bản mới","Bản nháp · Lưu trên thiết bị · AUTU-LIO Script 0.3")+
  '<p class="muted">Chọn mẫu có sẵn để bắt đầu, hoặc tự viết và sửa mã nguồn bên dưới. Android hiện có ScriptCompiler và trình soạn thảo riêng.</p>'+
  '<div class="actions two">'+button("Mẫu có sẵn","show-templates","outline")+button("Hướng dẫn","show-guide","outline")+'</div>'+
  '<div class="field"><label>Mã nguồn</label><textarea id="editor-source" placeholder="Nhập mã kịch bản hoặc chọn một mẫu...">'+esc(state.editorSource)+'</textarea></div>'+
  '<div class="actions two">'+button("Kiểm tra mã","check-code","outline")+button("Chuẩn bị chạy","prepare-run","primary")+'</div>'+
  '<div id="editor-feedback" class="muted">Bản Web chỉ xem bố cục; không gọi ScriptCompiler Android, không thực thi lệnh.</div>'+
  button("Lưu bản nháp trên thiết bị","save-draft","outline");
}
function renderBody(){
 if(state.page==="android-map")return androidMap();
 if(state.role==="OWNER"){
  if(state.page==="overview")return adminOverview();
  if(state.page==="scripts")return adminScripts();
  if(state.page==="customers")return customerList();
  if(state.page==="devices")return adminDevices();
  if(state.page==="more")return adminMore();
  if(state.page==="editor")return editorScreen();
  if(state.page==="more-sub")return nestedAdmin();
  if(state.page==="projects")return nestedAdmin();
  return adminOverview();
 }
 if(state.role==="CUSTOMER"){
  if(state.page==="home")return customerHome();
  if(state.page==="scripts")return customerScripts();
  if(state.page==="device")return customerDevice();
  if(state.page==="settings")return customerSettings();
 }
 if(state.role==="DEVELOPER"){
  if(state.page==="collaborator-projects")return collaboratorProjects();
  if(state.page==="developer-editor")return editorScreen();
  return developerWorkspace();
 }
 if(state.role==="DESIGNER"){
  if(state.page==="collaborator-projects")return collaboratorProjects();
  return designerWorkspace();
 }
 if(state.role==="REVIEWER"){
  if(state.page==="collaborator-projects")return collaboratorProjects();
  return reviewerWorkspace();
 }
 return "";
}
function renderModal(){
 const old=document.querySelector(".modal-backdrop"); if(old)old.remove();
 if(!state.modal)return;
 let content="";
 if(state.modal==="create"){
  content='<div class="modal"><div class="modal-head"><div><small class="eyebrow">AUTU-LIO</small><h2>Tạo kịch bản</h2></div><button class="btn outline" data-action="close-modal">✕</button></div><form id="create-form"><div class="field"><label>Tên kịch bản</label><input id="new-script-name" required maxlength="80" placeholder="Tên AUTU"></div><div class="modal-actions"><button type="button" class="btn outline" data-action="close-modal">Hủy</button><button type="submit" class="btn primary">Tạo bản nháp</button></div></form></div>';
 }else if(state.modal==="templates"){
  content='<div class="modal"><div class="modal-head"><div><small class="eyebrow">THƯ VIỆN MẪU</small><h2>Chọn mẫu AUTU có sẵn</h2></div><button class="btn outline" data-action="close-modal">✕</button></div><div class="menu-list">'+templates.map((t,i)=>'<button class="menu-item" data-template="'+i+'"><span class="mi-icon">⌘</span><span class="grow"><b>'+esc(t.name)+'</b><small>Chèn mẫu vào vùng mã nguồn</small></span><span class="chevron">›</span></button>').join("")+'</div></div>';
 }else{
  content='<div class="modal"><div class="modal-head"><h2>Hướng dẫn AUTU-LIO Script</h2><button class="btn outline" data-action="close-modal">✕</button></div><p>Ví dụ mã nguồn Android:</p><pre>CHẠM(120, 480)\nCHỜ(500)\nNHẤN_GIỮ(300, 700, 800)\nVUỐT(500, 900, 500, 300, 450)</pre><p class="muted">Bộ kiểm tra thật nằm trong APK Android. Bản Web không biên dịch hoặc chạy thao tác.</p></div>';
 }
 const backdrop=document.createElement("div");backdrop.className="modal-backdrop";backdrop.innerHTML=content;$("app").appendChild(backdrop);
}
function parseRunnerVariables(source){
 const found=new Map();
 const pattern=/ĐẶT\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(?:"([^"]*)"|'([^']*)'|(-?\\d+(?:\\.\\d+)?)|(ĐÚNG|SAI))/g;
 let match;
 while((match=pattern.exec(source||""))!==null){
  const name=match[1];
  if(found.has(name))continue;
  if(match[2]!==undefined)found.set(name,{name,type:"string",value:match[2]});
  else if(match[3]!==undefined)found.set(name,{name,type:"string",value:match[3]});
  else if(match[4]!==undefined)found.set(name,{name,type:"number",value:match[4]});
  else if(match[5]!==undefined)found.set(name,{name,type:"boolean",value:match[5]});
 }
 return [...found.values()];
}
function runnerStatus(){
 if(state.runnerState==="RUNNING")return "Đang chạy · Mô phỏng Web";
 if(state.runnerState==="PAUSED")return "Đã tạm dừng · Mô phỏng Web";
 if(state.runnerState==="CANCELLED")return "Đã dừng · Có thể chạy lại";
 if(state.runnerState==="COMPLETED")return "Hoàn tất · Có thể chạy lại";
 return "Sẵn sàng · Chuyển sang ứng dụng cần thao tác rồi nhấn Chạy";
}
function runnerOverlay(){
 if(!state.runnerVisible)return "";
 const collapsed=state.runnerCollapsed;
 const status=runnerStatus();
 const pos=state.runnerPos?("left:"+state.runnerPos.x+"px;top:"+state.runnerPos.y+"px;right:auto;bottom:auto;"):"";
 const editable=state.runnerState==="RUNNING"||state.runnerState==="PAUSED";
 const variables=state.runnerVariables||[];
 const parameterPanel=state.runnerEditorOpen?
  '<section class="runner-params"><b>THAM SỐ KỊCH BẢN</b><p>Đây là giá trị mẫu lấy từ các lệnh ĐẶT; Web không chạy ScriptCompiler hay engine Android.</p>'+
  (variables.length?variables.map(v=>'<label class="runner-var"><span>'+esc(v.name)+'</span><input data-runner-var="'+esc(v.name)+'" data-type="'+v.type+'" value="'+esc(v.value)+'" '+(v.type==="number"?'inputmode="decimal"':'')+'></label>').join(""):'<p class="muted">Kịch bản chưa có biến ĐẶT để minh họa.</p>')+
  '<div class="actions two"><button class="btn primary" data-action="runner-apply" '+(!variables.length?"disabled":"")+'>Cập nhật</button><button class="btn outline" data-action="runner-editor-close">Đóng</button></div></section>':"";
 return '<aside class="floating-runner '+(collapsed?"collapsed":"")+'" style="'+pos+'">'+
  '<div class="runner-head" data-runner-drag><span class="runner-grip">⠿</span><span class="runner-brand"><i></i><b>AUTU-LIO</b><small>ĐIỀU KHIỂN</small></span>'+
  '<button data-action="runner-collapse" aria-label="'+(collapsed?"Mở rộng":"Thu gọn")+'">'+(collapsed?"▴":"▾")+'</button>'+
  '<button data-action="runner-close" aria-label="Đóng cửa sổ">×</button></div>'+
  (collapsed?"":'<div class="runner-body"><div class="runner-status">'+esc(status)+'</div>'+
   '<div class="runner-controls"><button class="runner-main" data-action="runner-primary">'+(state.runnerState==="RUNNING"?"Ⅱ":"▶")+'</button>'+
   '<button class="runner-stop" data-action="runner-stop">■</button>'+
   '<button class="runner-parameters" data-action="runner-params" '+(!editable?"disabled":"")+' title="Sửa tham số khi tạm dừng">⚙</button></div>'+
   '<p class="runner-note">Bản xem trước chỉ mô phỏng cửa sổ nổi; không chạm, vuốt hay điều khiển ứng dụng khác.</p>'+parameterPanel+'</div>')+
  '</aside>';
}
let runnerDrag=null;
document.addEventListener("pointerdown",(event)=>{
 const handle=event.target.closest("[data-runner-drag]");
 if(!handle||event.target.closest("button"))return;
 const panel=document.querySelector(".floating-runner");
 if(!panel)return;
 const rect=panel.getBoundingClientRect();
 runnerDrag={panel,startX:event.clientX,startY:event.clientY,left:rect.left,top:rect.top};
 try{handle.setPointerCapture(event.pointerId);}catch(_){}
});
document.addEventListener("pointermove",(event)=>{
 if(!runnerDrag)return;
 const panel=runnerDrag.panel;
 const x=Math.max(0,Math.min(window.innerWidth-panel.offsetWidth,runnerDrag.left+event.clientX-runnerDrag.startX));
 const y=Math.max(0,Math.min(window.innerHeight-panel.offsetHeight,runnerDrag.top+event.clientY-runnerDrag.startY));
 panel.style.left=x+"px";panel.style.top=y+"px";panel.style.right="auto";panel.style.bottom="auto";
});
document.addEventListener("pointerup",()=>{
 if(runnerDrag){const rect=runnerDrag.panel.getBoundingClientRect();state.runnerPos={x:rect.left,y:rect.top};runnerDrag=null;}
});
function render(){
 const root=$("app");
 if(!state.role){root.innerHTML=state.previewChooser?chooser():authScreen();document.body.classList.toggle("theme-light",state.theme==="light");return;}
 root.innerHTML='<div class="app">'+header()+'<main class="content"><div class="preview-warning"><strong>Chế độ đối chiếu giao diện Android.</strong> Đây không phải APK, không đăng nhập Google, không truy cập dữ liệu thật và không điều khiển ứng dụng khác.</div>'+renderBody()+'</main>'+navBar()+runnerOverlay()+'</div>';
 document.body.classList.toggle("theme-light",state.theme==="light");
 document.body.dataset.accent=state.accent;
}
function setRole(role){
 state.role=role;
 state.previewChooser=false;
 state.adminSub="";
 state.page=role==="OWNER"?"overview":role==="CUSTOMER"?"home":"workspace";
 render();
}
function go(page){
 if(["collaborators","projects","licenses","library","packages","release","logs","settings"].includes(page)){
   state.adminSub=page;state.page="more-sub";
 }
 else {state.page=page;state.adminSub="";}
 render();
}
document.addEventListener("click",(event)=>{
 const role=event.target.closest("[data-role]");
 if(role){setRole(role.dataset.role);return;}
 const page=event.target.closest("[data-page]");
 if(page){const target=page.dataset.page;const sub=page.dataset.sub;if(sub){state.adminSub=sub;state.page="more-sub";render();}else if(target==="theme"){state.theme=state.theme==="dark"?"light":"dark";render();}else if(target==="signout"){state.role=null;state.page="";state.previewChooser=false;render();}else if(["about","privacy","security"].includes(target)){toast("Đây là màn hình thông tin trong bản đối chiếu.");}else go(target);return;}
 const filter=event.target.closest("[data-filter]");
 if(filter){state.scriptFilter=filter.dataset.filter;render();return;}
 const template=event.target.closest("[data-template]");
 if(template){const t=templates[Number(template.dataset.template)];if(t){state.editorName="Mẫu - "+t.name;state.editorSource=t.source;state.modal="";state.page="editor";render();}return;}
 const category=event.target.closest("[data-category]");
 if(category){state.libraryCategory=category.dataset.category;render();return;}
 const templateAction=event.target.closest("[data-action^='open-template:']");
 if(templateAction){const index=Number(templateAction.dataset.action.split(":")[1]);const t=templates[index];if(t){state.editorName="Mẫu - "+t.name;state.editorSource=t.source;state.page="editor";render();}return;}
 const color=event.target.closest("[data-color]");
 if(color){state.accent=color.dataset.color;render();return;}
 const inviteRole=event.target.closest("[data-invite-role]");
 if(inviteRole){state.inviteRole=inviteRole.dataset.inviteRole;render();return;}
 const action=event.target.closest("[data-action]");
 if(!action)return;
 const a=action.dataset.action;
 if(a==="open-android-map"){state.page="android-map";render();}
 else if(a==="back-android-map"){state.page=state.role==="OWNER"?"overview":state.role==="CUSTOMER"?"home":"workspace";render();}
 if(a==="back-roles"){state.role=null;state.page="";state.modal="";state.previewChooser=true;render();}
 else if(a==="open-role-chooser"){state.previewChooser=true;render();}
 else if(a==="back-auth"){state.previewChooser=false;render();}
 else if(a==="sign-in"){toast("Đăng nhập Google chưa hoạt động trong bản Web đối chiếu.");}
 else if(a==="theme"){state.theme=state.theme==="dark"?"light":"dark";render();}
 else if(a==="create-script"){createScriptDialog();}
 else if(a==="close-modal"){state.modal="";renderModal();}
 else if(a==="more"){state.page="more";state.adminSub="";render();}
 else if(a==="go-collaborators"){state.adminSub="collaborators";state.page="more-sub";render();}
 else if(a.startsWith("open-draft:")){
   const i=Number(a.split(":")[1]);const d=state.drafts[i];if(d){state.editorName=d.name;state.editorSource=d.source;state.page="editor";render();}
 }
 
 else if(a==="scripts"){state.page="scripts";render();}
 else if(a==="device"){state.page="device";render();}
 else if(a==="server-check"){toast("Máy chủ AUTU-LIO chưa được kết nối trong bản xem trước.");}
 
 else if(a==="check-code"){const node=$("editor-feedback");if(node)node.textContent="Chưa kiểm tra: bản Web không gọi ScriptCompiler của Android.";}
 else if(a==="prepare-run"){
   const src=$("editor-source");if(src)state.editorSource=src.value;
   state.runnerVariables=parseRunnerVariables(state.editorSource);
   state.runnerVisible=true;state.runnerState="IDLE";state.runnerCollapsed=false;state.runnerEditorOpen=false;state.runnerPos=null;render();
 }
 else if(a==="runner-primary"){
   if(state.runnerState==="RUNNING")state.runnerState="PAUSED";
   else state.runnerState="RUNNING";
   state.runnerEditorOpen=false;render();
 }
 else if(a==="runner-stop"){state.runnerState="CANCELLED";state.runnerEditorOpen=false;render();}
 else if(a==="runner-params"){
   if(state.runnerState==="RUNNING")state.runnerState="PAUSED";
   if(state.runnerState==="PAUSED"){state.runnerEditorOpen=true;render();}
   else toast("Hãy nhấn Chạy trước khi mở phần tham số.");
 }
 else if(a==="runner-apply"){
   const fields=[...document.querySelectorAll("[data-runner-var]")];
   const values=[];
   for(const field of fields){
    const original=state.runnerVariables.find(v=>v.name===field.dataset.runnerVar);
    let value=field.value;
    if(original.type==="number"){const number=Number(value);if(!Number.isFinite(number)){toast("Giá trị số không hợp lệ.");return;}value=String(number);}
    else if(original.type==="boolean"){const normalized=value.trim().toUpperCase();if(!["ĐÚNG","SAI"].includes(normalized)){toast("Giá trị đúng/sai phải là ĐÚNG hoặc SAI.");return;}value=normalized;}
    else if(value.length>4096){toast("Chuỗi không được vượt quá 4.096 ký tự.");return;}
    values.push({name:original.name,type:original.type,value});
   }
   state.runnerVariables=values;state.runnerEditorOpen=true;render();toast("Đã cập nhật giá trị trong bản mô phỏng Web.");
 }
 else if(a==="runner-editor-close"){state.runnerEditorOpen=false;render();}
 else if(a==="runner-collapse"){state.runnerCollapsed=!state.runnerCollapsed;state.runnerEditorOpen=false;render();}
 else if(a==="runner-close"){state.runnerVisible=false;state.runnerState="CANCELLED";state.runnerEditorOpen=false;render();}
 else if(a==="save-draft"){const src=$("editor-source");state.editorSource=src?src.value:state.editorSource;toast("Bản xem trước Web chưa lưu vào kho bản nháp Android.");}
 else if(a==="show-templates"){state.modal="templates";renderModal();}
 else if(a==="show-guide"){state.modal="guide";renderModal();}
 else if(a==="editor-back"){state.page=state.role==="DEVELOPER"?"workspace":"scripts";render();}
 else if(a==="developer-editor"){const n=$("developer-script-name");state.editorName=n&&n.value.trim()?n.value.trim():"Kịch bản mới";state.editorSource="";state.page="developer-editor";render();}
 else if(a==="collaborator-projects"){state.page="collaborator-projects";render();}
 else if(a==="back-workspace"){state.page="workspace";render();}
 else if(a==="invite-draft"){
   const input=$("invite-email"),email=input?input.value.trim():"";
   const valid=/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
   if(!valid)toast("Vui lòng nhập Gmail hợp lệ.");
   else if((state.invites||[]).some(x=>x.email.toLowerCase()===email.toLowerCase()))toast("Gmail này đã có trong danh sách bản nháp.");
   else{state.invites.push({email,role:state.inviteRole==="DEVELOPER"?"Nhà phát triển":state.inviteRole==="DESIGNER"?"Nhà thiết kế":"Người kiểm duyệt"});render();toast("Đã thêm bản nháp trong phiên xem Web; chưa gửi lời mời thật.");}
 }
 else if(a.startsWith("remove-invite:")){const i=Number(a.split(":")[1]);if(Number.isInteger(i)&&i>=0){state.invites.splice(i,1);render();}}
 else if(a==="accept-invite"){toast("Chức năng này yêu cầu đăng nhập Google và API thật.");}
 else if(a==="signout"){state.role=null;state.page="";state.previewChooser=false;render();}
 else if(a==="about"||a==="privacy"||a==="security"){toast("Đây là màn hình thông tin trong bản đối chiếu.");}
});
document.addEventListener("input",(event)=>{
 if(event.target.id==="script-search"){state.scriptQuery=event.target.value;const pos=event.target.selectionStart;const root=$("app");const main=root.querySelector(".content");if(main){main.innerHTML=renderBody();const input=$("script-search");if(input){input.focus();input.setSelectionRange(pos,pos);}}}
 if(event.target.id==="editor-source")state.editorSource=event.target.value;
 if(event.target.id==="library-search"){
  state.libraryQuery=event.target.value;
  const list=$("library-results");if(list)list.innerHTML=libraryCards();
  const count=$("library-count");if(count)count.textContent=libraryCountLabel();
 }
});
document.addEventListener("submit",(event)=>{
 if(event.target.id==="create-form"){
  event.preventDefault();const name=$("new-script-name").value.trim();if(!name)return;
  if(state.drafts.some(d=>d.name.toLowerCase()===name.toLowerCase())){toast("Tên kịch bản đã tồn tại.");return;}
  state.drafts.push({name,source:""});state.modal="";state.page="scripts";render();toast("Đã thêm vào danh sách xem trước trên trình duyệt.");
 }
});
render();
})();