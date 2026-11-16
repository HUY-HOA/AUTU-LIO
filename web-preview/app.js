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
  role:null, page:"", adminSub:"", theme:"dark", scriptFilter:"all", scriptQuery:"",
  libraryQuery:"", editorName:"", editorSource:"", drafts:[], modal:"", accent:"blue",
  reviewerProject:"", statusMessage:""
};
const templates = [
  {name:"Chạm một lần",source:"CHẠM(120, 480)\n"},
  {name:"Chạm theo chuỗi",source:"CHẠM(120, 480)\nCHỜ(500)\nCHẠM(240, 600)\n"},
  {name:"Nhấn giữ",source:"NHẤN_GIỮ(300, 700, 800)\n"},
  {name:"Vuốt",source:"VUỐT(500, 900, 500, 300, 450)\n"},
  {name:"Lặp thao tác",source:"LẶP(3) {\n    CHẠM(120, 480)\n    CHỜ(250)\n}\n"}
];
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
function chooser(){
  return '<div class="chooser"><div class="chooser-card">'+brandHeader(false)+heading("Chọn giao diện kiểm thử","Một ứng dụng AUTU-LIO, giao diện được mở theo quyền tài khoản.")+
    notice("Chế độ xem thử dành cho bản Debug","Các lựa chọn dưới đây chỉ kiểm tra bố cục. Chúng không đăng nhập và không cấp quyền thật.","info")+
    '<div class="role-list">'+roles.map(r=>'<button class="role-card" data-role="'+r.id+'"><span class="role-icon">'+r.icon+'</span><span class="grow"><b>'+r.title+'</b><small>'+r.sub+'</small></span><span class="chevron">›</span></button>').join("")+'</div></div></div>';
}
function header(){
 const role=roles.find(r=>r.id===state.role);
 return '<header class="topbar"><div class="brand"><div class="brand-mark">A</div><div><div class="brand-name">AUTU-LIO</div><div class="brand-sub">'+esc(role?role.title:"Bản xem trước")+'</div></div></div><span class="preview-label">DEBUG PREVIEW</span><button class="small-link" data-action="back-roles">Đổi vai trò</button></header>';
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
  '<p class="muted">Bản nháp được lưu trên thiết bị này; chưa đồng bộ máy chủ.</p>'+
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
   body=notice("Bản xem trước lời mời","Trong Android Debug, lời mời được lưu thành bản nháp cục bộ. Chưa gửi lời mời và chưa cấp quyền vì máy chủ chưa kết nối.","warn")+
   '<div class="field"><label>Địa chỉ Gmail</label><input id="invite-email" placeholder="name@gmail.com"></div>'+
   '<div class="field"><label>Chọn vai trò</label><select id="invite-role"><option>Nhà phát triển</option><option>Nhà thiết kế</option><option>Người kiểm duyệt</option></select></div>'+
   button("＋  Thêm bản nháp lời mời","invite-draft","primary")+
   '<div class="section-title">Danh sách cộng tác viên dự kiến</div>'+empty("♙","Chưa có bản nháp","Thêm Gmail ở trên để chuẩn bị phân quyền.");
 }else if(state.adminSub==="projects"){
   body=notice("Chưa có phiên đăng nhập","Đăng nhập lại để quản lý dự án cộng tác trên máy chủ.","warn")+empty("▤","Chưa có dự án","Dự án và thành viên chỉ xuất hiện sau khi dịch vụ xác thực và API được kết nối.");
 }else if(state.adminSub==="licenses"){
   body=card("Thông tin giấy phép",'<p>Khách hàng · Gói sử dụng · Thời hạn · Giới hạn thiết bị · Trạng thái</p>')+empty("◇","Chưa có giấy phép","Dữ liệu giấy phép sẽ xuất hiện sau khi kết nối máy chủ.");
 }else if(state.adminSub==="library"){
   body='<div class="field"><input id="library-search" placeholder="Tìm theo tên, mô tả hoặc lệnh"></div>'+
    '<div class="section-title">Mẫu AUTU-LIO có sẵn</div>'+templates.map((t,i)=>'<button class="menu-item" data-template="'+i+'"><span class="mi-icon">⌘</span><span class="grow"><b>'+esc(t.name)+'</b><small>Mẫu mã nguồn để chèn vào trình soạn thảo</small></span><span class="chevron">›</span></button>').join("");
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
 return brandHeader(false)+heading("Kịch bản","Các kịch bản do chủ ứng dụng cung cấp")+
  card('<span class="status warn">MẪU XEM TRƯỚC · KHÔNG PHẢI GÓI THẬT</span>'+
   '<div class="row between"><b>Kịch bản minh họa</b><span class="chevron">›</span></div>',
   '<p>Màn hình mẫu để kiểm tra luồng xem chi tiết và các nút điều khiển. Không chứa thao tác tự động hóa thật.</p>'+button("Xem chi tiết mẫu","customer-detail","outline"))+
  notice("Kho kịch bản chưa kết nối","Kịch bản thật sẽ xuất hiện sau khi đăng nhập và đồng bộ giấy phép.","warn");
}
function customerDetail(){
 return brandHeader(false)+button("← Quay lại danh sách","scripts","outline")+heading("Kịch bản minh họa","Chi tiết bản xem trước")+
  card('<span class="status warn">CHỈ XEM TRƯỚC</span><div class="section-title">Mô tả</div>',
   '<p>Đây là nội dung minh họa để kiểm tra bố cục. Chưa có package được phát hành hoặc tải về thiết bị.</p>')+
  notice("Chưa sẵn sàng chạy","Runtime AutoClick và quyền trợ năng chưa được tích hợp.","warn")+
  '<div class="section-title">Bảng điều khiển</div>'+button("▶  Thử nút chạy (không thực thi)","sample-run","primary")+
  '<div class="actions two">'+button("Ⅱ  Tạm dừng mẫu","sample-pause","outline")+button("■  Dừng mẫu","sample-stop","outline")+'</div>'+
  '<p class="muted">Các nút trên chỉ kiểm tra giao diện và thông báo. Chúng không chạm, vuốt hoặc điều khiển ứng dụng khác.</p>';
}
function customerDevice(){
 return brandHeader(false)+heading("Thiết bị và giấy phép","Thông tin đăng ký của tài khoản")+
  notice("Chưa đăng nhập thật","Đây là chế độ xem trước. Chưa có dữ liệu thiết bị hoặc giấy phép được truy xuất.","warn")+
  '<div class="section-title">Thiết bị hiện tại</div>'+card("▣  Thông tin thiết bị",'<p>Chỉ hiển thị sau khi đăng nhập và đồng ý cung cấp dữ liệu cần thiết.</p>')+
  '<div class="section-title">Giấy phép</div>'+empty("◇","Chưa có thông tin giấy phép","Gói sử dụng, thời hạn và giới hạn thiết bị sẽ hiển thị sau khi máy chủ xác minh tài khoản.");
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
 return '<div class="workspace-top"><div><b>Không gian nhà phát triển</b><small>developer@example · Bản xem trước</small></div>'+button("Đăng xuất","signout","outline")+'</div>'+
  heading("Kịch bản của nhà phát triển","Mã nguồn được chỉnh sửa trong cửa sổ trình soạn thảo riêng.")+
  button("▤  Mở dự án cộng tác","collaborator-projects","outline")+
  '<div class="field"><label>Tên kịch bản</label><input id="developer-script-name" placeholder="Tên kịch bản"></div>'+
  '<div class="section-title">Bản nháp đã lưu</div>'+empty("⌘","Chưa có bản nháp","Nhập tên rồi mở trình soạn thảo riêng để tạo kịch bản.")+
  button("✎  Mở trình soạn thảo riêng","developer-editor","primary")+
  notice("Lưu trữ bản nháp","Bản nháp nhà phát triển được mã hóa và lưu riêng, không dùng chung kho bản nháp của Chủ sở hữu. Lưu cục bộ chưa có nghĩa là đã đồng bộ hoặc gửi duyệt.","info")+
  card("Quyền đã cấp",'<p>Quyền sửa mã, kiểm thử hoặc gửi duyệt phụ thuộc quyền do Chủ sở hữu cấp.</p>');
}
function designerWorkspace(){
 return '<div class="workspace-top"><div><b>Không gian nhà thiết kế</b><small>designer@example · Bản xem trước</small></div>'+button("Đăng xuất","signout","outline")+'</div>'+
  heading("Bản xem trước giao diện","Thiết kế bố cục và màu sắc trong khu vực được giao.")+
  button("▤  Mở dự án thiết kế","collaborator-projects","outline")+
  '<section class="designer-preview" id="designer-preview"><div class="row"><span class="icon-box" id="designer-icon">✣</span><div><b>AUTU-LIO</b><p class="muted">Tự động hóa thao tác</p></div></div><div class="section-title">Kịch bản của bạn</div><div class="sample-row"><b>Kịch bản mẫu</b><small>Bản xem trước — chưa phát hành</small></div><button class="btn primary" disabled>Nút xem trước</button></section>'+
  '<div class="section-title">Chọn màu nhấn</div><div class="actions three">'+
  '<button class="filter active" data-color="blue">● Blue</button><button class="filter" data-color="green">● Green</button><button class="filter" data-color="violet">● Violet</button></div>'+
  notice("Bản xem trước cục bộ","Thay đổi hiện chỉ là bản xem trước cục bộ. Vai trò nhà thiết kế không được xem hoặc sửa mã kịch bản, giấy phép hay phát hành.","info")+
  card("Quyền đã cấp",'<p>Chỉnh sửa giao diện · Xem trước · Gửi đề xuất (tùy quyền được cấp).</p>');
}
function reviewerWorkspace(){
 return '<div class="workspace-top"><div><b>Không gian kiểm duyệt</b><small>reviewer@example · Bản xem trước</small></div>'+button("Đăng xuất","signout","outline")+'</div>'+
  heading("Kiểm tra bản gửi duyệt","Bạn chỉ xem và đánh giá các bản được gửi vào hàng chờ.")+
  button("▤  Mở hàng đợi dự án","collaborator-projects","outline")+
  notice("Chưa có bản phát hành nào được gửi duyệt","Khi kết nối máy chủ, các bản gửi duyệt sẽ xuất hiện ở đây.","warn")+
  card("Quyền kiểm duyệt",'<p>Xem mã được giao · Chạy kiểm thử · Duyệt hoặc từ chối đề xuất</p><p>Không có quyền phát hành cuối cùng hoặc quản lý tài khoản.</p>')+
  card("Quyền đã cấp",'<p>Quyền kiểm thử và duyệt bản gửi phụ thuộc Chủ sở hữu.</p>');
}
function collaboratorProjects(){
 return '<div class="workspace-top"><div><b>Dự án cộng tác</b><small>Không gian dự án được giao</small></div>'+button("← Quay lại","back-workspace","outline")+'</div>'+
  heading("Dự án cộng tác","Các dự án và bản nháp dùng chung theo quyền tài khoản.")+
  notice("Chưa có phiên đăng nhập thật","Bản xem trước Web không kết nối danh tính hoặc API dự án của tài khoản Android.","warn")+
  empty("▤","Chưa có dự án được giao","Dự án sẽ xuất hiện sau khi đăng nhập và chủ sở hữu phân công.");
}
function editorScreen(){
 return brandHeader(true)+button("← Quay lại","editor-back","outline")+
  heading(state.editorName||"Kịch bản mới","Bản nháp · Lưu trên thiết bị")+
  '<p class="muted">Chọn mẫu có sẵn để bắt đầu, hoặc tự viết và sửa mã nguồn bên dưới. Android hiện có ScriptCompiler và trình soạn thảo riêng.</p>'+
  '<div class="actions two">'+button("Mẫu có sẵn","show-templates","outline")+button("Hướng dẫn","show-guide","outline")+'</div>'+
  '<div class="field"><label>Mã nguồn</label><textarea id="editor-source" placeholder="Nhập mã kịch bản hoặc chọn một mẫu...">'+esc(state.editorSource)+'</textarea></div>'+
  '<div class="actions two">'+button("Kiểm tra mã","check-code","outline")+button("Chuẩn bị chạy","prepare-run","primary")+'</div>'+
  '<div id="editor-feedback" class="muted">Bản Web chỉ xem bố cục; không gọi ScriptCompiler Android, không thực thi lệnh.</div>'+
  button("Lưu bản nháp trên thiết bị","save-draft","outline");
}
function renderBody(){
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
  if(state.page==="customer-detail")return customerDetail();
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
function render(){
 const root=$("app");
 if(!state.role){root.innerHTML=chooser();return;}
 root.innerHTML='<div class="app">'+header()+'<main class="content"><div class="preview-warning"><strong>Chế độ đối chiếu giao diện Android.</strong> Đây không phải APK, không đăng nhập Google, không truy cập dữ liệu thật và không điều khiển ứng dụng khác.</div>'+renderBody()+'</main>'+navBar()+'</div>';
 document.body.classList.toggle("theme-light",state.theme==="light");
 document.body.dataset.accent=state.accent;
}
function setRole(role){
 state.role=role;
 state.adminSub="";
 state.page=role==="OWNER"?"overview":role==="CUSTOMER"?"home":"workspace";
 render();
}
function go(page){
 if(["collaborators","projects","licenses","library","packages","release","logs","settings"].includes(page)){
   state.adminSub=page;state.page="more-sub";
 }else if(page==="customer-detail"){state.page=page;}
 else {state.page=page;state.adminSub="";}
 render();
}
document.addEventListener("click",(event)=>{
 const role=event.target.closest("[data-role]");
 if(role){setRole(role.dataset.role);return;}
 const page=event.target.closest("[data-page]");
 if(page){const sub=page.dataset.sub;if(sub){state.adminSub=sub;state.page="more-sub";render();}else go(page.dataset.page);return;}
 const filter=event.target.closest("[data-filter]");
 if(filter){state.scriptFilter=filter.dataset.filter;render();return;}
 const template=event.target.closest("[data-template]");
 if(template){const t=templates[Number(template.dataset.template)];if(t){state.editorSource=t.source;state.modal="";state.page="editor";render();}return;}
 const color=event.target.closest("[data-color]");
 if(color){state.accent=color.dataset.color;render();return;}
 const action=event.target.closest("[data-action]");
 if(!action)return;
 const a=action.dataset.action;
 if(a==="back-roles"){state.role=null;state.page="";state.modal="";render();}
 else if(a==="theme"){state.theme=state.theme==="dark"?"light":"dark";render();}
 else if(a==="create-script"){createScriptDialog();}
 else if(a==="close-modal"){state.modal="";renderModal();}
 else if(a==="more"){state.page="more";state.adminSub="";render();}
 else if(a==="go-collaborators"){state.adminSub="collaborators";state.page="more-sub";render();}
 else if(a==="open-draft:"+a.split(":")[1] || a.startsWith("open-draft:")){
   const i=Number(a.split(":")[1]);const d=state.drafts[i];if(d){state.editorName=d.name;state.editorSource=d.source;state.page="editor";render();}
 }
 else if(a==="customer-detail"){state.page="customer-detail";render();}
 else if(a==="scripts"){state.page="scripts";render();}
 else if(a==="device"){state.page="device";render();}
 else if(a==="server-check"){toast("Máy chủ AUTU-LIO chưa được kết nối trong bản xem trước.");}
 else if(a==="sample-run"||a==="sample-pause"||a==="sample-stop"){toast("Đây chỉ là nút xem trước; không có thao tác nào được thực thi.");}
 else if(a==="check-code"){const node=$("editor-feedback");if(node)node.textContent="Chưa kiểm tra: bản Web không gọi ScriptCompiler của Android.";}
 else if(a==="prepare-run"){toast("Bản Web không khởi chạy AccessibilityService hoặc thao tác trên ứng dụng khác.");}
 else if(a==="save-draft"){const src=$("editor-source");state.editorSource=src?src.value:state.editorSource;toast("Bản xem trước Web chưa lưu vào kho bản nháp Android.");}
 else if(a==="show-templates"){state.modal="templates";renderModal();}
 else if(a==="show-guide"){state.modal="guide";renderModal();}
 else if(a==="editor-back"){state.page=state.role==="DEVELOPER"?"workspace":"scripts";render();}
 else if(a==="developer-editor"){const n=$("developer-script-name");state.editorName=n&&n.value.trim()?n.value.trim():"Kịch bản mới";state.editorSource="";state.page="developer-editor";render();}
 else if(a==="collaborator-projects"){state.page="collaborator-projects";render();}
 else if(a==="back-workspace"){state.page="workspace";render();}
 else if(a==="invite-draft"){toast("Bản Web chưa gửi lời mời hoặc cấp quyền thật.");}
 else if(a==="accept-invite"){toast("Chức năng này yêu cầu đăng nhập Google và API thật.");}
 else if(a==="signout"){state.role=null;state.page="";render();}
 else if(a==="about"||a==="privacy"||a==="security"){toast("Đây là màn hình thông tin trong bản đối chiếu.");}
});
document.addEventListener("input",(event)=>{
 if(event.target.id==="script-search"){state.scriptQuery=event.target.value;const pos=event.target.selectionStart;const root=$("app");const main=root.querySelector(".content");if(main){main.innerHTML=renderBody();const input=$("script-search");if(input){input.focus();input.setSelectionRange(pos,pos);}}}
 if(event.target.id==="editor-source")state.editorSource=event.target.value;
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