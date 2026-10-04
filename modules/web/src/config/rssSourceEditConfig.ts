export default {
  base: {
    name: 'Cơ bản',
    children: [
      {
        title: 'Tên miền nguồn',
        id: 'sourceUrl',
        type: 'String',
        hint: 'Thường là trang chủ, ví dụ: https://www.qidian.com',
        required: true,
      },
      {
        title: 'Biểu tượng',
        id: 'sourceIcon',
        type: 'String',
        hint: 'Nhập liên kết ảnh',
      },
      {
        title: 'Tên nguồn',
        id: 'sourceName',
        type: 'String',
        hint: 'Hiển thị trong danh sách nguồn',
        required: true,
      },
      {
        title: 'Nhóm nguồn',
        id: 'sourceGroup',
        type: 'String',
        hint: 'Mô tả đặc điểm của nguồn',
      },
      {
        title: 'Ghi chú nguồn',
        id: 'sourceComment',
        type: 'String',
        hint: 'Mô tả tác giả và trạng thái nguồn',
      },
      {
        title: 'Địa chỉ tìm kiếm',
        id: 'searchUrl',
        type: 'String',
        hint: '[có thể bỏ tên miền]/search.php@kw={{key}}',
      },
      {
        title: 'Địa chỉ phân loại',
        id: 'sortUrl',
        type: 'String',
        hint: 'Tên1::Liên_kết1\nTên2::Liên_kết2',
      },
      {
        title: 'Địa chỉ đăng nhập',
        id: 'loginUrl',
        type: 'String',
        hint: 'Nhập URL đăng nhập, chỉ cần với nguồn yêu cầu đăng nhập',
      },
      {
        title: 'Giao diện đăng nhập',
        id: 'loginUi',
        type: 'String',
        hint: 'Giao diện đăng nhập tùy chỉnh',
      },
      {
        title: 'Kiểm tra đăng nhập',
        id: 'loginCheckJs',
        type: 'String',
        hint: 'JS kiểm tra đăng nhập',
      },
      {
        title: 'Giải mã ảnh bìa',
        id: 'coverDecodeJs',
        type: 'String',
        hint: 'JS giải mã ảnh bìa',
      },
      {
        title: 'Header yêu cầu',
        id: 'header',
        type: 'String',
        hint: 'Định danh trình khách',
      },
      {
        title: 'Mô tả biến',
        id: 'variableComment',
        type: 'String',
        hint: 'Mô tả biến của nguồn',
      },
      {
        title: 'Giới hạn yêu cầu',
        id: 'concurrentRate',
        type: 'String',
        hint: 'Giới hạn yêu cầu',
      },
      {
        title: 'Thư viện JS',
        id: 'jsLib',
        type: 'String',
        hint: 'Nhập JS hoặc đối tượng key-value để tải tệp JS trực tuyến',
      },
    ],
  },
  start: {
    name: 'Khởi động',
    children: [
      {
        title: 'HTML trang khởi động',
        id: 'startHtml',
        type: 'String',
        hint: 'HTML trang khởi động',
      },
      {
        title: 'Kiểu trang khởi động',
        id: 'startStyle',
        type: 'String',
        hint: 'Nhập CSS cho trang khởi động',
      },
      {
        title: 'JS trang khởi động',
        id: 'startJs',
        type: 'String',
        hint: 'JS trang khởi động',
      },
      {
        title: 'Chèn JS trước khi tải',
        id: 'preloadJs',
        type: 'String',
        hint: 'JS được chèn trước khi trang tải.\nCó thể chèn các hàm JS của Legado.\nVí dụ: window.ajaxAwait = ajaxAwait;\nwindow.java = java;',
      },
    ],
  },
  list: {
    name: 'Danh sách',
    children: [
      {
        title: 'Quy tắc danh sách',
        id: 'ruleArticles',
        type: 'String',
        hint: 'Kết quả quy tắc là List<Element>',
      },
      {
        title: 'Quy tắc trang tiếp',
        id: 'ruleNextPage',
        type: 'String',
        hint: 'Liên kết trang kế tiếp; kết quả là List<String> hoặc String',
      },
      {
        title: 'Quy tắc tiêu đề',
        id: 'ruleTitle',
        type: 'String',
        hint: 'Tiêu đề bài, kết quả String',
      },
      {
        title: 'Quy tắc thời gian',
        id: 'rulePubDate',
        type: 'String',
        hint: 'Thời gian đăng bài, kết quả String',
      },
      {
        title: 'Quy tắc mô tả',
        id: 'ruleDescription',
        type: 'String',
        hint: 'Mô tả ngắn của bài, kết quả String',
      },
      {
        title: 'Quy tắc ảnh',
        id: 'ruleImage',
        type: 'String',
        hint: 'Liên kết ảnh của bài, kết quả String',
      },
      {
        title: 'Quy tắc liên kết',
        id: 'ruleLink',
        type: 'String',
        hint: 'Liên kết bài, kết quả String',
      },
    ],
  },
  webView: {
    name: 'WebView',
    children: [
      {
        title: 'Quy tắc nội dung',
        id: 'ruleContent',
        type: 'String',
        hint: 'Nội dung bài',
      },
      {
        title: 'Quy tắc kiểu hiển thị',
        id: 'style',
        type: 'String',
        hint: 'Nhập CSS cho nội dung bài',
      },
      {
        title: 'Quy tắc chèn mã',
        id: 'injectJs',
        type: 'String',
        hint: 'JavaScript chèn vào trang',
      },
      {
        title: 'Danh sách chặn',
        id: 'contentBlacklist',
        type: 'String',
        hint: 'Các liên kết WebView bị chặn, phân cách bằng dấu phẩy',
      },
      {
        title: 'Danh sách cho phép',
        id: 'contentWhitelist',
        type: 'String',
        hint: 'Các liên kết WebView được phép tải, phân cách bằng dấu phẩy',
      },
      {
        title: 'Chặn liên kết',
        id: 'shouldOverrideUrlLoading',
        type: 'String',
        hint: 'Nhập JS; biến url là liên kết tài nguyên hiện tại, trả về true để chặn',
      },
    ],
  },
  other: {
    name: 'Khác',
    children: [
      {
        title: 'Loại nguồn',
        id: 'type',
        type: 'Array',
        array: ['Trang web', 'Hình ảnh', 'Video'],
      },
      {
        title: 'Kiểu danh sách',
        id: 'articleStyle',
        type: 'Array',
        array: ['Mặc định', 'Ảnh lớn', 'Hai cột', 'Xếp so le', 'Ba cột'],
      },
      {
        title: 'Tải trước',
        id: 'preload',
        type: 'Boolean',
      },
      {
        title: 'Tải địa chỉ',
        id: 'loadWithBaseUrl',
        type: 'Boolean',
      },
      {
        title: 'Bật JS',
        id: 'enableJs',
        type: 'Boolean',
      },
      {
        title: 'Xuất WebLog',
        id: 'showWebLog',
        type: 'Boolean',
      },
      {
        title: 'Ưu tiên bộ nhớ đệm',
        id: 'cacheFirst',
        type: 'Boolean',
      },
      {
        title: 'Bật',
        id: 'enabled',
        type: 'Boolean',
      },
      {
        title: 'Cookie',
        id: 'enabledCookieJar',
        type: 'Boolean',
      },
      {
        title: 'URL đơn',
        id: 'singleUrl',
        type: 'Boolean',
      },
      {
        title: 'Thứ tự sắp xếp',
        id: 'customOrder',
        type: 'Number',
      },
    ],
  },
}
