// The only browser script: a theme preference, no network or analytics.
(() => {
  let theme = "dark";
  try {
    theme =
      localStorage.getItem("decryptum-theme") === "light" ? "light" : "dark";
  } catch {}
  document.documentElement.dataset.theme = theme;
  document.addEventListener("DOMContentLoaded", () => {
    const button = document.querySelector("button[data-theme-toggle]");
    if (!(button instanceof HTMLButtonElement)) return;
    button.hidden = false;
    const update = () => {
      const dark = document.documentElement.dataset.theme === "dark";
      button.textContent = dark ? "Light theme" : "Dark theme";
      button.setAttribute(
        "aria-label",
        dark ? "Switch to light theme" : "Switch to dark theme",
      );
    };
    update();
    button.addEventListener("click", () => {
      const next =
        document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      document.documentElement.dataset.theme = next;
      try {
        localStorage.setItem("decryptum-theme", next);
      } catch {}
      update();
    });
  });
})();
