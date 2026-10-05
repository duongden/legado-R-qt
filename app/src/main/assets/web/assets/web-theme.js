(() => {
  const media = matchMedia("(prefers-color-scheme: dark)");
  const options = {
    system: "Theo hệ thống",
    light: "Sáng",
    dark: "Tối",
    oled: "Đen OLED",
    paper: "Giấy ngà",
    gray: "Xám dịu",
  };
  const read = (key) => {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  };
  let chosen = read("legado_ui_theme_v1") || "system";
  function apply() {
    const mode =
      chosen === "system" ? (media.matches ? "dark" : "light") : chosen;
    document.documentElement.dataset.theme = mode;
    document.documentElement.style.colorScheme = ["dark", "oled"].includes(mode)
      ? "dark"
      : "light";
    let palette;
    try {
      palette = JSON.parse(read("legado_ui_palettes_v1") || "{}")[mode];
    } catch {}
    for (const key of ["page", "surface", "reader", "ink", "muted", "accent"]) {
      const value = palette?.[key];
      if (/^#[0-9a-f]{6}$/i.test(value))
        document.documentElement.style.setProperty("--" + key, value);
      else document.documentElement.style.removeProperty("--" + key);
    }
  }
  apply();
  media.addEventListener("change", apply);
  addEventListener("storage", () => {
    chosen = read("legado_ui_theme_v1") || "system";
    apply();
    const select = document.querySelector(".web-appearance select");
    if (select) select.value = chosen;
  });
  addEventListener("DOMContentLoaded", () => {
    const bar = document.createElement("nav");
    bar.className = "web-appearance";
    bar.setAttribute("aria-label", "Giao diện");
    const label = document.createElement("label");
    label.textContent = "Giao diện ";
    const select = document.createElement("select");
    select.setAttribute("aria-label", "Chế độ giao diện");
    for (const [value, text] of Object.entries(options)) {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = text;
      select.append(option);
    }
    select.value = chosen;
    select.onchange = () => {
      chosen = select.value;
      try {
        localStorage.setItem("legado_ui_theme_v1", chosen);
      } catch {}
      apply();
    };
    label.append(select);
    const link = document.createElement("a");
    link.href = "/vue/index.html#/";
    link.textContent = "Tùy chỉnh màu";
    bar.append(label, link);
    const slot = document.getElementById("web-appearance-slot");
    if (slot) slot.append(bar);
    else document.body.prepend(bar);
  });
})();
