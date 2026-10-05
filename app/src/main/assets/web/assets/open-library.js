// Keep a single main page and avoid a redirect loop when navigating Back.
location.replace(new URL('vue/index.html#/', location.href).href);
