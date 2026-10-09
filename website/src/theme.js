// Local theme preference and progressive motion; no network or analytics.
(() => {
  let theme = "dark";
  try {
    theme =
      localStorage.getItem("decryptum-theme") === "light" ? "light" : "dark";
  } catch {}
  document.documentElement.dataset.theme = theme;
  document.addEventListener("DOMContentLoaded", () => {
    const root = document.documentElement;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const revealTargets = document.querySelectorAll(
      ".hero-copy > *, .hero-preview, .principles span, .section-heading, " +
        ".feature, .totp-preview > *, .capture-gallery figure, .inline-note, " +
        ".privacy-layout > div, .data-list > div, .architecture article, " +
        ".center-copy, .check-list > div, .advisor > div, .roadmap > div, " +
        ".roadmap li, .faq > .eyebrow, .faq > h2, .faq details, " +
        ".source-cta > *, .footer-inner > *, .doc > h1, .doc > h2, " +
        ".doc > h3, .doc > p, .doc > ul, .doc > ol, .doc > .table-scroll",
    );
    /** @type {IntersectionObserver | undefined} */
    let observer;
    const revealAll = () => {
      observer?.disconnect();
      revealTargets.forEach((element) => element.classList.add("is-visible"));
    };
    if (!reducedMotion.matches && "IntersectionObserver" in window) {
      observer = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (!entry.isIntersecting) continue;
            entry.target.classList.add("is-visible");
            observer?.unobserve(entry.target);
          }
        },
        { threshold: 0.06, rootMargin: "0px 0px -20px 0px" },
      );
      revealTargets.forEach((element, index) => {
        element.classList.add("reveal", `reveal-step-${index % 4}`);
        observer?.observe(element);
      });
      root.dataset.motion = "ready";
    }
    reducedMotion.addEventListener("change", (event) => {
      if (event.matches) revealAll();
    });
    document.addEventListener("focusin", (event) => {
      if (event.target instanceof Element) {
        let parent = event.target.closest(".reveal");
        while (parent) {
          parent.classList.add("is-visible");
          observer?.unobserve(parent);
          parent = parent.parentElement?.closest(".reveal") ?? null;
        }
      }
    });
    // CSS scroll timelines own the progress indicator; older browsers omit it.
    const progress = document.createElement("div");
    progress.className = "reading-progress";
    progress.setAttribute("aria-hidden", "true");
    document.body.append(progress);
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
