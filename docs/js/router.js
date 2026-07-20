/**
 * router.js — Path-based SPA router
 * Routes:
 *   /              → Home
 *   /overview      → Docs Overview
 *   /config        → Configuration
 *   /config/blocks → Blocks
 *   /config/drops  → Drops
 *   /config/tools  → Tools
 *   /config/nodes  → Nodes
 *   /models        → Models / BDEngine
 *   /holograms     → Holograms
 *   /api           → API Reference
 */
const Router = {
  routes: {},
  currentRoute: null,
  outletId: 'page-content',
  homeOutletId: 'home-content',
  sidebarId: 'sidebar-container',
  footerId: 'footer-container',
  docsLayoutId: 'docs-layout',
  homeWrapperId: 'home-wrapper',
  onPageLoad: null,
  pendingScrollTarget: null,
  sectionAnchors: ['about', 'features', 'roadmap', 'usage'],

  /**
   * Register a callback called after every page load.
   * @param {Function} fn - Receives the path
   */
  onAfterResolve(fn) {
    this.onPageLoad = fn;
  },

  /**
   * Define routes.
   * @param {Object} routes - Map of path to page component path
   */
  define(routes) {
    this.routes = routes;
  },

  /**
   * Set the outlet element ID where page content is rendered.
   */
  setOutlet(id) {
    this.outletId = id;
  },

  /**
   * Get the current path (normalized).
   */
  getHash() {
    let path = window.location.pathname;
    // Remove trailing slash (except for root)
    if (path !== '/' && path.endsWith('/')) path = path.slice(0, -1);
    return path || '/';
  },

  /**
   * Navigate to a route using history.pushState.
   */
  navigate(path) {
    window.history.pushState(null, '', path);
    this.resolve();
  },

  /**
   * Check if a path is a docs route (not home).
   */
  _isDocsRoute(path) {
    return path !== '/' && path !== '' && !this.sectionAnchors.includes(path);
  },

  /**
   * Handle route change — called when popstate fires or navigate is called.
   */
  async resolve() {
    const hash = this.getHash();
    if (hash === this.currentRoute) return;

    // Check if hash targets an existing element on the current page (TOC intra-page anchor)
    if (hash && hash !== '/' && hash !== '' && !this.routes[hash] && !this.sectionAnchors.includes(hash)) {
      const targetEl = document.getElementById(hash);
      if (targetEl) {
        this.currentRoute = hash;
        targetEl.scrollIntoView({ behavior: 'smooth' });
        return;
      }
    }

    this.currentRoute = hash;

    // Find matching route
    let pagePath = this.routes[hash];
    if (!pagePath) {
      for (const [pattern, path] of Object.entries(this.routes)) {
        if (hash.startsWith(pattern + '/') || hash === pattern) {
          pagePath = path;
          break;
        }
      }
    }
    if (!pagePath) {
      pagePath = this.routes['/'];
    }

    const isDocs = this._isDocsRoute(hash);
    const docsLayout = document.getElementById(this.docsLayoutId);
    const homeWrapper = document.getElementById(this.homeWrapperId);
    const footerContainer = document.getElementById(this.footerId);

    // If navigating to a section anchor from a docs page, ensure sidebar is cleared
    if (this.sectionAnchors.includes(hash)) {
      const sidebarEl = document.getElementById(this.sidebarId);
      if (sidebarEl) sidebarEl.innerHTML = '';
    }

    // Toggle docs layout vs home wrapper
    if (isDocs) {
      docsLayout.style.display = 'flex';
      homeWrapper.style.display = 'none';
      if (footerContainer) footerContainer.style.display = 'none';

      // Load sidebar into sidebar container
      const sidebarEl = document.getElementById(this.sidebarId);
      if (sidebarEl && !sidebarEl.hasChildNodes()) {
        try {
          const sidebarRes = await fetch('/components/sidebar.html');
          if (sidebarRes.ok) {
            sidebarEl.innerHTML = await sidebarRes.text();
          }
        } catch (e) {
          // silently fail
        }
      }

      // Show loading in the docs outlet
      const outlet = document.getElementById(this.outletId);
      if (!outlet) return;
      outlet.innerHTML = '<div class="loading-container"><div class="loading-spinner"></div></div>';

      try {
        const res = await fetch(pagePath);
        if (!res.ok) throw new Error(`Failed to load page: ${pagePath}`);
        const html = await res.text();
        outlet.innerHTML = html;

        // Update document title
        const titleMatch = html.match(/<title>([^<]+)<\/title>/i);
        if (titleMatch) document.title = titleMatch[1];

        window.scrollTo(0, 0);

        // Transform code blocks
        CodeBlock.transform();

        // Replace version placeholders
        Version.init();

        // Re-run scroll reveal observer
        this._initReveal();

        // Update sidebar active state
        this._updateSidebar(hash);

        // Fire global after-page-load callback
        if (typeof this.onPageLoad === 'function') {
          this.onPageLoad(hash);
        }

      } catch (err) {
        outlet.innerHTML = `<div class="loading-container"><p style="color:var(--red)">Failed to load page: ${err.message}</p></div>`;
      }

    } else {
      // Home route
      docsLayout.style.display = 'none';
      homeWrapper.style.display = 'block';

      const outlet = document.getElementById(this.homeOutletId);
      if (!outlet) return;

      // Show loading
      outlet.innerHTML = '<div class="loading-container"><div class="loading-spinner"></div></div>';

      try {
        const res = await fetch(pagePath);
        if (!res.ok) throw new Error(`Failed to load page: ${pagePath}`);
        const html = await res.text();
        outlet.innerHTML = html;

        // Update document title
        const titleMatch = html.match(/<title>([^<]+)<\/title>/i);
        if (titleMatch) document.title = titleMatch[1];

        // Transform code blocks
        CodeBlock.transform();

        // Replace version placeholders
        Version.init();

        // Re-run scroll reveal observer
        this._initReveal();

        // Load footer (only on home page)
        this._loadFooter();

        // Handle pending scroll target (section anchors from navbar)
        const isSectionAnchor = this.sectionAnchors.includes(hash);
        if (this.pendingScrollTarget || isSectionAnchor) {
          const target = this.pendingScrollTarget || hash;
          this.pendingScrollTarget = null;
          setTimeout(() => {
            const el = document.getElementById(target);
            if (el) el.scrollIntoView({ behavior: 'smooth' });
          }, 150);
        } else {
          window.scrollTo(0, 0);
        }

        // Fire global after-page-load callback
        if (typeof this.onPageLoad === 'function') {
          this.onPageLoad(hash);
        }

      } catch (err) {
        outlet.innerHTML = `<div class="loading-container"><p style="color:var(--red)">Failed to load page: ${err.message}</p></div>`;
      }
    }
  },

  /**
   * Load footer into footer container (only for home page).
   */
  async _loadFooter() {
    const footerContainer = document.getElementById(this.footerId);
    if (!footerContainer) return;
    try {
      const res = await fetch('/components/footer.html');
      if (res.ok) {
        footerContainer.innerHTML = await res.text();
        footerContainer.style.display = 'block';
      }
    } catch (e) {
      // silently fail
    }
  },

  /**
   * Initialize IntersectionObserver for .reveal elements.
   */
  _initReveal() {
    const observer = new IntersectionObserver(entries => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          e.target.classList.add('visible');
          observer.unobserve(e.target);
        }
      });
    }, { threshold: 0.1 });
    document.querySelectorAll('.reveal').forEach(el => observer.observe(el));
  },

  /**
   * Update sidebar active link based on current path.
   */
  _updateSidebar(path) {
    document.querySelectorAll('.sidebar-link').forEach(link => {
      link.classList.remove('active');
      const href = link.getAttribute('href');
      if (href === path) {
        link.classList.add('active');
      }
    });
  },

  /**
   * Start the router.
   */
  start() {
    // Listen for browser back/forward
    window.addEventListener('popstate', () => this.resolve());

    // Intercept link clicks to SPA routes
    document.addEventListener('click', e => {
      const link = e.target.closest('a[href]');
      if (!link) return;
      const href = link.getAttribute('href');
      if (!href) return;

      // Skip external links, mailto, etc.
      if (href.startsWith('http') || href.startsWith('//') || href.startsWith('mailto:')) return;

      // Handle section anchors (#about, #features, etc.)
      if (href.startsWith('#')) {
        const hash = href.substring(1);
        if (this.sectionAnchors.includes(hash)) {
          e.preventDefault();
          if (this.currentRoute === '/' || this.currentRoute === '') {
            // Already on home page, scroll directly
            setTimeout(() => {
              const el = document.getElementById(hash);
              if (el) el.scrollIntoView({ behavior: 'smooth' });
            }, 100);
          } else {
            this.pendingScrollTarget = hash;
            this.navigate('/');
          }
          return;
        }
        // Otherwise (TOC intra-page anchors), let the browser handle natively
        return;
      }

      // Resolve the target path
      const path = href.startsWith('/') ? href : '/' + href;

      // Check if it matches a registered SPA route (exact or prefix)
      if (this.routes[path]) {
        e.preventDefault();
        this.navigate(path);
        return;
      }
      for (const [pattern] of Object.entries(this.routes)) {
        if (path.startsWith(pattern + '/') || path === pattern) {
          e.preventDefault();
          this.navigate(path);
          return;
        }
      }
      // Not a known SPA route — let the browser navigate normally
    });

    // Resolve initial route
    this.resolve();
  }
};
