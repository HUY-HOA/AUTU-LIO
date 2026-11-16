const $ = (id) => document.getElementById(id);
const state = { role:null, page:"", theme:"dark", editorSource:"", selectedTemplate:"", toastTimer:null, scriptFilter:"all", scriptQuery:"", libraryCategory:"all", libraryQuery:"" };
const roles = {
  owner:{label:"Chủ sở hữu",subtitle:"Quản trị ứng dụng, kịch bản và quyền sử dụng",icon:"⌂"},
  developer:{label:"Nhà phát triển",subtitle:"Workspace mã và dự án được phân công",icon:"⌘"},
  designer:{label:"Nhà thiết kế",subtitle:"Workspace thiết kế giao diện",icon:"✧"},
  reviewer:{label:"Người kiểm duyệt",subtitle:"Hàng đợi kiểm tra và duyệt đề xuất",icon:"✓"},
  customer:{label:"Người dùng",subtitle:"Sử dụng các gói AUTU được phát hành",icon:"♙"}
};
const ownerTabs = [["overview","Tổng quan","⌂"],["scripts","Kịch bản","▤"],["customers","Khách hàng","♙"],["devices","Thiết bị","▣"],["more","Thêm","•••"]];
const customerTabs = [["home","Trang chủ","⌂"],["scripts","Kịch bản","▤"],["device","Thiết bị","▣"],["settings","Cài đặt","⚙"]];
const moreItems = [
  ["collaborators","Cộng tác viên","Mời và phân quyền nhà phát triển, nhà thiết kế, người kiểm duyệt","♙"],
  ["projects","Dự án cộng tác","Chia dự án cho các thành viên đã được mời","▧"],
  ["licenses","Giấy phép","Cấp, gia hạn và thu hồi quyền sử dụng","✓"],
  ["library","Thư viện mẫu AUTU","Quản lý và chọn mẫu AUTU có sẵn","▤"],
  ["packages","Gói AUTU","Mã hóa, phát hành phiên bản và phân phối AUTU","⌘"],
  ["release","Phát hành","Quản lý phiên bản và phân phối","⇧"],
  ["logs","Nhật ký","Theo dõi sự kiện và lỗi hệ thống","◷"],
  ["settings","Cài đặt","Giao diện, bảo mật và kết nối máy chủ","⚙"]
];
const templates = [
 ["Chạm một lần","Chạm tại một tọa độ trên màn hình.","CHẠM(200, 400)"],
 ["Chuỗi chạm có thời gian chờ","Chạm hai vị trí, giữa hai lần chạm chờ 500 ms.","CHẠM(200, 400)\nCHỜ(500)\nCHẠM(500, 400)"],
 ["Nhấn giữ","Nhấn giữ tại một vị trí trong 800 ms.","NHẤN_GIỮ(200, 400, 800)"],
 ["Vuốt lên","Vuốt từ dưới lên trên trong 450 ms.","VUỐT(360, 850, 360, 350, 450)"],
 ["Lặp thao tác chạm","Lặp thao tác chạm theo số lần đã đặt.","ĐẶT soLan = 3\nLẶP(soLan) {\n  CHẠM(200, 400)\n  CHỜ(500)\n}"],
 ["Kiểm tra pin trước khi chạy","Chỉ tiếp tục thao tác khi pin còn trên 20%.","NẾU (PIN() > 20) {\n  CHẠM(200, 400)\n} NGƯỢC_LẠI {\n  CHỜ(1000)\n}"],
 ["Mở ứng dụng","Mở ứng dụng bằng tên gói Android hợp lệ.","MỞ_ỨNG_DỤNG(\"com.example.game\")"],
 ["Lặp theo điều kiện","Tăng biến đếm và dừng lặp khi đạt điều kiện.","ĐẶT dem = 0\nLẶP_KHI (dem < 5) {\n  dem = dem + 1\n  CHẠM(200, 400)\n  CHỜ(300)\n}"],
 ["Chờ điều kiện sẵn sàng","Kiểm tra điều kiện định kỳ và dừng khi hết thời hạn.","CHỜ_ĐẾN_KHI(ỨNG_DỤNG_ĐANG_MỞ(\"com.example.game\"), 15000, 250)\nCHẠM(200, 400)"],
 ["Lặp đến khi đạt mục tiêu","Thực hiện thao tác đến khi đạt điều kiện dừng.","ĐẶT dem = 0\nLẶP_ĐẾN_KHI(dem >= 5) {\n  dem = dem + 1\n  CHẠM(200, 400)\n  CHỜ(300)\n}"],
 ["Quay lại","Thực hiện thao tác Quay lại của Android.","QUAY_LẠI()"],
 ["Về màn hình chính","Đưa thiết bị về màn hình chính Android.","VỀ_MÀN_HÌNH_CHÍNH()"],
 ["Mở ứng dụng gần đây","Mở giao diện ứng dụng gần đây của Android.","ỨNG_DỤNG_GẦN_ĐÂY()"],
 ["Mở thông báo","Mở ngăn thông báo Android.","MỞ_THÔNG_BÁO()"],
 ["Mở cài đặt nhanh","Mở bảng cài đặt nhanh Android.","MỞ_CÀI_ĐẶT_NHANH()"],
 ["Hiện menu nguồn","Mở menu nguồn Android.","HIỆN_MENU_NGUỒN()"],
 ["Khóa màn hình","Khóa màn hình nếu phiên bản Android hỗ trợ.","KHÓA_MÀN_HÌNH()"],
 ["Chia đôi màn hình","Bật chia đôi nếu Android hỗ trợ.","CHIA_MÀN_HÌNH()"]
];
function esc(value){return String(value).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]));}
function notify(message){const el=$("toast");el.textContent=message;el.classList.remove("hidden");clearTimeout(state.toastTimer);state.toastTimer=setTimeout(()=>el.classList.add("hidden"),3000);}
function heading(kicker,title,subtitle){return '<div class="eyebrow">'+kicker+'</div><h1 class="page-title">'+title+'</h1><p class="page-sub">'+subtitle+'</p>';}
function card(title,body,extra){return '<section class="card">'+(title?'<div class="card-head"><h3>'+title+'</h3>'+(extra||"")+'</div>':"")+body+'</section>';}
function unavailable(title,description){return card("",'<div class="empty-state"><div class="empty-icon">◌</div><strong>'+title+'</strong><p>'+description+'</p></div>');}
function actionRow(icon,title,description,page){return '<button class="action-row" data-page="'+page+'"><span class="action-icon">'+icon+'</span><span><strong>'+title+'</strong><small>'+description+'</small></span><span class="action-arrow">›</span></button>';}
function renderChooser(){
 $("topbar").classList.add("hidden");$("bottom-nav").classList.add("hidden");$("back-to-roles").classList.add("hidden");
 const list=Object.entries(roles).map(([key,r])=>'<button type="button" class="role-card" data-role="'+key+'"><span class="action-icon">'+r.icon+'</span><span><strong>'+r.label+'</strong><small>'+r.subtitle+'</small></span><span class="action-arrow">›</span></button>').join("");
 $("screen").innerHTML='<div style="padding-top:10px"><div class="brand-mark" style="width:54px;height:54px;font-size:23px;margin-bottom:20px">A</div><div class="eyebrow">ANDROID · DEBUG PREVIEW</div><h1 class="page-title" style="font-size:29px;margin-top:8px">AUTU-LIO</h1><p class="page-sub">Một ứng dụng duy nhất. Giao diện được mở theo vai trò tài khoản.</p><div class="status-note blue">Đây là màn hình chọn vai trò xem trước trong bản Debug Android. Bản phát hành thật phải xác minh vai trò từ máy chủ, không tự cấp quyền tại đây.</div><h3 style="font-size:14px;margin:23px 0 10px">Chọn giao diện để đối chiếu</h3><div class="role-grid">'+list+'</div></div>';
}
function navMarkup(tabs){return tabs.map(([key,label,icon])=>{const active=state.page===key||(state.role==="owner"&&key==="more"&&!ownerTabs.some(t=>t[0]===state.page));return '<button type="button" class="nav-btn '+(active?'active':'')+'" data-page="'+key+'"><span class="nav-icon">'+icon+'</span><span>'+label+'</span></button>';}).join("");}
function setRole(role){state.role=role;state.page=role==="owner"?"overview":role==="customer"?"home":"workspace";render();}
function renderShell(){
 $("topbar").classList.remove("hidden");$("top-title").textContent=state.role==="owner"?"AUTU-LIO · Chủ sở hữu":roles[state.role].label;
 $("top-subtitle").textContent="Bản đối chiếu giao diện từ mã Android";
 $("back-to-roles").classList.remove("hidden");
 const tabs=state.role==="owner"?ownerTabs:state.role==="customer"?customerTabs:null;
 if(tabs){$("bottom-nav").classList.remove("hidden");$("bottom-nav").innerHTML=navMarkup(tabs);}
 else {$("bottom-nav").classList.add("hidden");$("bottom-nav").innerHTML="";}
}
function scriptListEmpty(){
 if((state.scriptQuery||"").trim())return unavailable("Không tìm thấy kịch bản phù hợp","Không có bản nháp Android nào được chia sẻ sang trình duyệt.");
 if(state.scriptFilter==="published")return unavailable("Chưa có kịch bản phát hành","Các kịch bản đã phát hành sẽ xuất hiện tại đây sau khi được kết nối.");
 return unavailable("Chưa có kịch bản nào hiển thị","Tạo kịch bản đầu tiên trong APK Android để bắt đầu. Các bản nháp đã lưu trên điện thoại không được đọc từ Web.");
}
function renderOwner(){
 switch(state.page){
 case "overview":
  return heading("OWNER · TỔNG QUAN","Tổng quan","Quản lý kịch bản, khách hàng, thiết bị, giấy phép và gói AUTU.")+
   '<div class="status-note"><b>Chưa có phiên đăng nhập thật.</b><br>Đang xem vai trò Owner ở chế độ Debug Preview. Dữ liệu khách hàng, thiết bị, giấy phép và gói chỉ tải khi có phiên đăng nhập hợp lệ và máy chủ hoạt động.</div>'+
   '<div class="grid"><div class="metric"><small>Khách hàng</small><strong>—</strong></div><div class="metric"><small>Thiết bị</small><strong>—</strong></div><div class="metric"><small>Giấy phép hoạt động</small><strong>—</strong></div><div class="metric"><small>Gói AUTU hoạt động</small><strong>—</strong></div></div>'+
   card("Truy cập nhanh",'<div class="action-list">'+actionRow("＋","Tạo AUTU","Mở danh sách kịch bản để tạo mới","scripts")+actionRow("▤","Kịch bản","Mở các bản nháp được lưu trên thiết bị","scripts")+actionRow("♙","Khách hàng","Quản lý tài khoản khách hàng","customers")+actionRow("▣","Thiết bị","Quản lý thiết bị Android đã đăng ký","devices")+actionRow("✓","Giấy phép","Cấp, gia hạn hoặc thu hồi quyền","licenses")+actionRow("⇧","Phát hành gói","Quản lý gói AUTU","packages")+'</div>');
 case "scripts":
  return heading("OWNER · KỊCH BẢN","Kịch bản","Tạo, tìm kiếm và quản lý các bản nháp AUTU.")+
   '<div class="btn-row"><button class="btn primary full" data-page="editor">＋ Thêm AUTU</button></div>'+
   '<label class="field"><span>Tìm kiếm kịch bản</span><input id="script-search" value="'+esc(state.scriptQuery||"")+'" placeholder="Tìm kiếm kịch bản"></label>'+
   '<div class="color-row" style="margin:8px 0 12px">'+[["all","Tất cả"],["draft","Bản nháp"],["published","Đã phát hành"]].map(x=>'<button class="color-chip" data-script-filter="'+x[0]+'" style="'+(state.scriptFilter===x[0]?'border-color:var(--blue);color:#a9c7ff':'')+'">'+x[1]+'</button>').join("")+'</div>'+
   '<div class="status-note blue">Bản nháp được lưu trên thiết bị Android này; chưa đồng bộ máy chủ. Web không truy cập được dữ liệu đã mã hóa trong điện thoại.</div>'+
   '<div id="script-list">'+scriptListEmpty()+'</div>'; 
 case "customers":
  return heading("OWNER · KHÁCH HÀNG","Khách hàng","Theo dõi tài khoản, giấy phép và quyền sử dụng.")+
   unavailable("Chưa có phiên đăng nhập thật","Màn hình Android lấy danh sách từ Auth API. Bản Debug xem thử đang dùng accessToken = null nên không có dữ liệu khách hàng trực tiếp.");
 case "devices":
  return heading("OWNER · THIẾT BỊ","Thiết bị","Theo dõi thiết bị đã đăng ký và trạng thái hoạt động.")+
   unavailable("Chưa có phiên đăng nhập thật","Không có danh sách thiết bị thật để hiển thị trong chế độ xem thử. Không sử dụng dữ liệu thiết bị giả.");
 case "more":
  return heading("OWNER · QUẢN LÝ","Thêm","Các khu vực quản trị bổ sung của Chủ sở hữu.")+
   '<div class="action-list">'+moreItems.map(x=>actionRow(x[3],x[1],x[2],x[0])).join("")+'</div>'+
   card("Giao diện và tài khoản",'<div class="action-list">'+actionRow("◐","Chuyển giao diện sáng / tối","Đổi màu giao diện của bản Web xem trước","theme")+'</div><div class="status-note blue">Đăng xuất thật cần phiên Google đã xác thực. Bản xem thử không tạo tài khoản hoặc phiên đăng nhập.</div>');
 case "editor": return renderEditor();
 case "library": return renderLibrary();
 case "script-templates": return renderScriptTemplates();
 case "collaborators": return heading("OWNER · CỘNG TÁC VIÊN","Cộng tác viên","Mời và phân quyền nhà phát triển, nhà thiết kế và người kiểm duyệt.")+unavailable("Chưa kết nối phiên máy chủ","Giao diện và API quản lý cộng tác viên có trong mã Android. Cần đăng nhập thật và cấu hình Auth API để đọc hoặc thay đổi dữ liệu.");
 case "projects": return heading("OWNER · DỰ ÁN","Dự án cộng tác","Tạo dự án và thêm các cộng tác viên đã được mời.")+unavailable("Chưa có dự án trực tuyến","Workspace dự án lưu dữ liệu qua Worker/D1. Bản Web này chưa đăng nhập và không đọc D1.");
 case "licenses": return heading("OWNER · GIẤY PHÉP","Giấy phép","Cấp, gia hạn và thu hồi quyền sử dụng.")+unavailable("Chưa có dữ liệu giấy phép","Màn hình Android cần phiên xác thực và phản hồi từ máy chủ. Không hiển thị bản ghi minh họa.");
 case "release": return heading("OWNER · PHÁT HÀNH","Phát hành","Quản lý phiên bản và phân phối gói AUTU.")+card("Quy trình phát hành",'<p>Kiểm tra · Tăng phiên bản · Ghi chú · Chọn kênh · Xác nhận · Phân phối.</p>')+unavailable("Chưa có bản phát hành","Chỉ phát hành khi gói đã được kiểm tra và ký xác thực.");
 case "logs": return heading("OWNER · NHẬT KÝ","Nhật ký","Theo dõi sự kiện và lỗi hệ thống.")+unavailable("Cần đăng nhập OWNER","Nhật ký được tải từ máy chủ theo tài khoản Owner.");
 case "packages": return heading("OWNER · GÓI AUTU","Gói AUTU","Quản lý bản nháp, mã hóa và phát hành phiên bản AUTU.")+
   '<div class="status-note blue">Mã Android có màn hình quản lý gói và quy trình phát hành. Bản Web không tạo, mã hóa, ký hoặc phân phối package thật.</div>'+
   unavailable("Chưa có phiên đăng nhập thật","Danh sách gói đã phát hành chỉ hiển thị sau khi kết nối API và xác thực Owner.");
 case "settings": return heading("OWNER · CÀI ĐẶT","Cài đặt","Giao diện, bảo mật và kết nối máy chủ.")+
   card("Giao diện",'<div class="btn-row"><button class="btn" data-action="theme">◐ Chuyển giao diện sáng / tối</button></div>')+
   card("Thông tin ứng dụng",'<p>AUTU-LIO · 0.1.0</p>')+
   card("Bảo mật",'<p>Vai trò và quyền được xác minh từ máy chủ. Bản Debug có bộ chọn xem trước; bộ chọn này không cấp quyền Admin ở bản phát hành.</p>')+
   card("Kết nối máy chủ",'<div class="status-note">Chưa có phiên đăng nhập thật. Kiểm tra máy chủ trong Web không xác minh được phiên trên Android.</div>')+
   '<div class="btn-row"><button class="btn" data-action="back-roles">Đăng xuất / Quay lại chọn vai trò</button></div>';
 default:return heading("OWNER","Màn hình chưa chọn","Chọn mục từ thanh điều hướng.");
 }
}
function renderEditor(){
 return heading("AUTU SCRIPT EDITOR","Trình soạn thảo AUTU","Màn hình riêng để viết mã, chọn mẫu, kiểm tra cú pháp và chuẩn bị chạy.")+
  '<div class="status-note blue">Đây là bản phản chiếu. Compiler Kotlin ScriptCompiler và bộ thực thi Android không chạy bên trong trình duyệt này.</div>'+
  card("Mã nguồn",'<textarea id="editor-source" class="code-area" spellcheck="false" placeholder="Nhập mã kịch bản hoặc chọn một mẫu...">'+esc(state.editorSource)+'</textarea>'+
  '<div class="btn-row"><button class="btn" data-page="script-templates">Mẫu có sẵn</button><button class="btn" data-page="guide">Hướng dẫn</button></div>'+
  '<div class="btn-row"><button class="btn primary" data-action="check-code">✓ Kiểm tra mã</button><button class="btn" disabled>Chuẩn bị chạy · Chỉ trong Android</button></div>'+
  '<div class="status-note">Bản nháp Android được lưu trên thiết bị. Web không ghi vào SecureScriptDraftStore hoặc Android Keystore.</div>');
}
function renderScriptTemplates(){
 return heading("AUTU SCRIPT · TEMPLATES","Mẫu có sẵn","Các mẫu được khai báo trong ScriptTemplateCatalog.kt.")+
 templates.map((t,i)=>'<div class="template"><div class="card-head"><strong>'+t[0]+'</strong><button class="btn" data-template="'+i+'">Dùng mẫu</button></div><small>'+t[1]+'</small><pre style="white-space:pre-wrap;color:#a8c9ff;font:11px/1.5 ui-monospace,monospace;margin:10px 0 0">'+esc(t[2])+'</pre></div>').join("")+
 '<div class="btn-row"><button class="btn" data-page="editor">← Quay lại trình soạn thảo</button></div>';
}
function renderGuide(){
 const commands=["CHẠM(x, y) · Chạm một lần","NHẤN_GIỮ(x, y, ms) · Nhấn giữ","VUỐT(x1, y1, x2, y2, ms) · Vuốt","CHỜ(ms) · Chờ","MỞ_ỨNG_DỤNG(\"tên.gói\") · Mở ứng dụng","ĐẶT ten = 3 · Khai báo biến","NẾU (điều_kiện) { ... } NGƯỢC_LẠI { ... }","LẶP(ten) { ... } · Lặp theo số lần","LẶP_KHI (điều_kiện) { ... }","LẶP_ĐẾN_KHI(điều_kiện) { ... }","CHỜ_ĐẾN_KHI(điều_kiện, thời_hạn_ms, chu_kỳ_ms)","PIN(), ĐANG_SẠC(), CÓ_MẠNG(), MÀN_HÌNH_SÁNG()"];
 return heading("AUTU SCRIPT · GUIDE","Hướng dẫn tạo và chạy AUTU","Các lệnh được hướng dẫn trong màn hình Editor của Android.")+
  card("Các lệnh đang hỗ trợ",'<div class="action-list">'+commands.map(c=>'<div class="template"><strong>'+esc(c)+'</strong></div>').join("")+'</div>')+
  '<div class="btn-row"><button class="btn" data-page="editor">← Quay lại trình soạn thảo</button></div>';
}
function libraryCards(){
 const categoryMap=["actions","actions","actions","actions","logic","logic","actions","logic","logic","logic","system","system","system","system","system","system","system","system"];
 const query=(state.libraryQuery||"").trim().toLocaleLowerCase("vi");
 const list=templates.map((t,i)=>({t,i,cat:categoryMap[i]})).filter(x=>(state.libraryCategory==="all"||x.cat===state.libraryCategory)&&(!query||x.t.join(" ").toLocaleLowerCase("vi").includes(query)));
 if(!list.length)return '<div class="empty-state"><div class="empty-icon">⌕</div><strong>Không tìm thấy mẫu phù hợp</strong><p>Thử từ khóa hoặc nhóm khác.</p></div>';
 return list.map(x=>'<div class="template"><div class="card-head"><strong>'+esc(x.t[0])+'</strong><span class="pill info">Mẫu có sẵn</span></div><small>'+esc(x.t[1])+'</small><pre style="white-space:pre-wrap;color:#a8c9ff;font:11px/1.5 ui-monospace,monospace;margin:10px 0">'+esc(x.t[2])+'</pre><button class="btn primary full" data-template-library="'+x.i+'">Mở và chỉnh sửa mẫu</button></div>').join("");
}
function renderLibrary(){
 state.libraryCategory=state.libraryCategory||"all";
 return heading("OWNER · THƯ VIỆN MẪU","Thư viện mẫu AUTU",templates.length+" mẫu lệnh có thể mở trực tiếp trong trình soạn thảo.")+
  card("Tương thích AUTU Script 0.1",'<p>Mỗi mẫu được kiểm tra cú pháp trong mã Android trước khi đưa vào thư viện.</p>')+
  '<label class="field"><span>Tìm theo tên, mô tả hoặc lệnh</span><input id="library-search" value="'+esc(state.libraryQuery||"")+'" placeholder="Tìm mẫu AUTU"></label>'+
  '<div class="color-row" style="margin:10px 0 14px">'+[["all","Tất cả"],["actions","Thao tác"],["logic","Điều kiện và vòng lặp"],["system","Android"]].map(x=>'<button class="color-chip" data-category="'+x[0]+'" style="'+(state.libraryCategory===x[0]?'border-color:var(--blue);color:#a9c7ff':'')+'">'+x[1]+'</button>').join("")+'</div>'+
  '<div class="eyebrow" style="margin:0 0 8px">MẪU CÓ SẴN</div><div id="library-results">'+libraryCards()+'</div>'+
  '<div class="btn-row"><button class="btn" data-page="more">← Quay lại</button></div>';
}

function renderCollaborator(){
 if(state.page==="editor")return renderEditor();
 if(state.page==="script-templates")return renderScriptTemplates();
 if(state.page==="guide")return renderGuide();
 if(state.page==="projects")return heading("DỰ ÁN CỘNG TÁC","Dự án được phân công","Các dự án hiển thị theo membership và quyền tài khoản.")+unavailable("Chưa tải được dự án","Bản xem thử không có access token hoặc kết nối phiên Worker đang hoạt động.");
 if(state.role==="developer"){
  return heading("DEVELOPER WORKSPACE","Không gian Nhà phát triển","Soạn thảo kịch bản và làm việc trong dự án được giao.")+
   '<div class="btn-row"><button class="btn primary full" data-page="projects">Mở dự án cộng tác</button></div>'+
   card("Tạo hoặc mở bản nháp",'<label class="field"><span>Tên kịch bản</span><input id="dev-script-name" placeholder="Tên kịch bản"></label><label class="field"><span>Mã nguồn</span><textarea class="code-area" id="dev-script-code" placeholder="Nhập nội dung AUTU..."></textarea></label><div class="btn-row"><button class="btn primary" data-page="editor">Mở trình soạn thảo riêng</button></div>')+
   card("Quyền đã cấp",'<p>Quyền phụ thuộc vào vai trò và quyền chi tiết mà Owner cấp trong máy chủ.</p><div class="status-note">Đang ở chế độ xem thử. Không có quyền tài khoản thật được cấp từ trang Web.</div>');
 }
 if(state.role==="designer"){
  return heading("DESIGNER WORKSPACE","Không gian Nhà thiết kế","Chỉnh giao diện được giao và xem trước thành phần.")+
   '<div class="btn-row"><button class="btn primary full" data-page="projects">Mở dự án thiết kế</button></div>'+
   card("Kịch bản mẫu",'<div style="border:1px solid var(--line);border-radius:14px;padding:18px;background:var(--surface2)"><div style="font-weight:800">AUTU-LIO</div><small style="color:var(--muted)">Tự động hóa thao tác</small><hr style="border:0;border-top:1px solid var(--line);margin:14px 0"><strong>Kịch bản mẫu</strong><p style="margin:6px 0 12px">Bản xem trước — chưa phát hành</p><button class="btn" disabled>Nút xem trước</button></div>')+
   card("Chọn màu nhấn",'<div class="color-row"><button class="color-chip" data-color="blue">Blue</button><button class="color-chip" data-color="green">Green</button><button class="color-chip" data-color="violet">Violet</button></div>')+
   card("Quyền đã cấp",'<p>Khả năng chỉnh sửa và xem trước phụ thuộc quyền mà Owner cấp.</p><div class="status-note">Bản Web chỉ đổi màu xem trước cục bộ; không ghi thiết kế lên dự án Android.</div>');
 }
 return heading("REVIEWER WORKSPACE","Không gian Người kiểm duyệt","Xem mã được giao, chạy kiểm thử và duyệt hoặc từ chối đề xuất.")+
  '<div class="btn-row"><button class="btn primary full" data-page="projects">Mở hàng đợi dự án</button></div>'+
  card("Quyền kiểm duyệt",'<p>Xem mã được giao · Chạy kiểm thử · Duyệt hoặc từ chối đề xuất.</p><p style="margin-top:8px">Không có quyền phát hành cuối cùng hoặc quản lý tài khoản.</p>')+
  unavailable("Chưa có đề xuất để duyệt","Hàng đợi thực tế lấy từ máy chủ theo quyền tài khoản. Web chưa được xác thực nên không tự tạo yêu cầu giả.");
}
function renderCustomer(){
 switch(state.page){
 case "home":
  return heading("AUTU-LIO · CUSTOMER","Trang chủ","Các gói AUTU được Chủ sở hữu phát hành cho tài khoản.")+
   '<div class="status-note">Tài khoản xem thử · Chưa có phiên đăng nhập thật. Dữ liệu gói và giấy phép cần Google Sign-In và Auth API.</div>'+
   card("Gói AUTU của bạn",'<div class="empty-state"><div class="empty-icon">⌘</div><strong>Chưa tải được gói AUTU</strong><p>Đăng nhập thật trong APK để đồng bộ gói đã được cấp.</p></div>')+
   '<div class="btn-row"><button class="btn primary" data-page="scripts">Mở kho AUTU</button><button class="btn" data-page="device">Thiết bị và giấy phép</button></div>';
 case "scripts":
  return heading("CUSTOMER · KỊCH BẢN","Kịch bản","Các kịch bản do Chủ sở hữu cung cấp.")+
   unavailable("Kho kịch bản chưa kết nối","Danh sách AUTU được tải từ máy chủ theo quyền tài khoản. Bản xem thử không hiển thị package mẫu giả.");
 case "device":
  return heading("CUSTOMER · THIẾT BỊ","Thiết bị và giấy phép","Thông tin đăng ký thiết bị, giới hạn và thời hạn sử dụng.")+
   unavailable("Chưa có phiên đăng nhập thật","Đăng ký thiết bị và kiểm tra giấy phép thực cần tài khoản đã xác thực và sự đồng ý của người dùng.");
 case "settings":
  return heading("CUSTOMER · CÀI ĐẶT","Cài đặt","Tùy chọn giao diện, lời mời và tài khoản.")+
   card("Giao diện",'<div class="btn-row"><button class="btn" data-action="theme">◐ Chuyển giao diện sáng / tối</button></div>')+
   card("Lời mời cộng tác",'<label class="field"><span>Mã lời mời</span><input placeholder="Dán mã lời mời tại đây"></label><div class="status-note">Chế độ xem thử không thể chấp nhận lời mời thật. Trong APK, máy chủ sẽ kiểm tra email Google và token một lần.</div>')+
   card("Tài khoản",'<p>Tài khoản xem thử</p>');
 default:return heading("CUSTOMER","Trang chủ","Chọn mục bên dưới.");
 }
}
function render(){
 if(!state.role){renderChooser();return;}
 renderShell();
 if(state.role==="owner")$("screen").innerHTML=renderOwner();
 else if(state.role==="customer")$("screen").innerHTML=renderCustomer();
 else $("screen").innerHTML=renderCollaborator();
 const top=state.page==="overview"?"Tổng quan":state.page==="scripts"?"Kịch bản":state.page==="customers"?"Khách hàng":state.page==="devices"?"Thiết bị":state.page==="more"?"Thêm":state.page==="home"?"Trang chủ":state.page==="device"?"Thiết bị":state.page==="settings"?"Cài đặt":state.page==="editor"?"Trình soạn thảo":state.page==="script-templates"?"Mẫu có sẵn":state.page==="library"?"Thư viện mẫu AUTU":state.page==="guide"?"Hướng dẫn":state.page==="projects"?"Dự án cộng tác":(moreItems.find(x=>x[0]===state.page)||[])[1]||roles[state.role].label;
 $("top-title").textContent=top;
}
function changePage(page){if(page==="theme"){toggleTheme();return;}state.page=page;render();}
function toggleTheme(){state.theme=state.theme==="dark"?"light":"dark";document.body.classList.toggle("theme-light",state.theme==="light");}
document.addEventListener("click",event=>{
 const role=event.target.closest("[data-role]");if(role){setRole(role.dataset.role);return;}
 const page=event.target.closest("[data-page]");if(page){changePage(page.dataset.page);return;}
 const action=event.target.closest("[data-action]");
 if(action){
  if(action.dataset.action==="theme")toggleTheme();
  else if(action.dataset.action==="check-code")notify("Kiểm tra cú pháp thật dùng ScriptCompiler trong APK Android.");
  else if(action.dataset.action==="back-roles"){state.role=null;state.page="";render();}
  return;
 }
 const scriptFilter=event.target.closest("[data-script-filter]");
 if(scriptFilter){state.scriptFilter=scriptFilter.dataset.scriptFilter;render();return;}
 const category=event.target.closest("[data-category]");
 if(category){state.libraryCategory=category.dataset.category;render();return;}
 const libraryTemplate=event.target.closest("[data-template-library]");
 if(libraryTemplate){const item=templates[Number(libraryTemplate.dataset.templateLibrary)];if(item){state.editorSource=item[2];state.page="editor";render();}return;}
 const template=event.target.closest("[data-template]");
 if(template){const item=templates[Number(template.dataset.template)];if(item){state.editorSource=item[2];state.page="editor";render();}return;}
 const color=event.target.closest("[data-color]");
 if(color){document.documentElement.style.setProperty("--blue",color.dataset.color==="green"?"#35b78b":color.dataset.color==="violet"?"#9a79ee":"#4f8cff");notify("Đổi màu xem trước trong trình duyệt; chưa lưu dự án Android.");return;}
});
$("back-to-roles").addEventListener("click",()=>{state.role=null;state.page="";render();});
$("theme-toggle").addEventListener("click",toggleTheme);
$("screen").addEventListener("input",event=>{if(event.target.id==="editor-source")state.editorSource=event.target.value;if(event.target.id==="library-search"){state.libraryQuery=event.target.value;const list=$("library-results");if(list)list.innerHTML=libraryCards();}if(event.target.id==="script-search"){state.scriptQuery=event.target.value;const list=$("script-list");if(list)list.innerHTML=scriptListEmpty();}});
render();