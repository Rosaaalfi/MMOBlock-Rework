/**
 * app.js — Main application entry point
 * Initializes the layout, router, and global features.
 */
const App = {
  /**
   * Initialize the application.
   */
  async init() {
    // 1. Load layout shell (nav + content containers)
    await Template.load('components/layout.html', '#app-shell', 'inner');

    // 2. Transform any code blocks in the layout
    CodeBlock.transform();

    // 3. Define routes
    Router.define({
      '/': '/components/pages/home.html',
      '/overview': '/components/pages/overview.html',
      '/config': '/components/pages/configuration.html',
      '/config/blocks': '/components/pages/blocks.html',
      '/config/drops': '/components/pages/drops.html',
      '/config/tools': '/components/pages/tools.html',
      '/config/nodes': '/components/pages/nodes.html',
      '/models': '/components/pages/models.html',
      '/holograms': '/components/pages/holograms.html',
      '/api': '/components/pages/api.html'
    });

    // 4. Set the outlet for page content
    Router.setOutlet('page-content');

    // 5. Register page-load callback for stats & version
    Router.onAfterResolve((hash) => {
      if (hash === '/' || hash === '') {
        App._fetchRepoStats();
      }
    });

    // 6. Start the router
    Router.start();
  },

  /**
   * Fetch GitHub repo stats for the hero section.
   */
  async _fetchRepoStats() {
    try {
      const res = await fetch('https://api.github.com/repos/Rosaaalfi/MMOBlock-Rework');
      if (!res.ok) return;
      const data = await res.json();
      const set = (id, val) => {
        const el = document.getElementById(id);
        if (el) {
          el.textContent = val ?? '–';
          el.classList.remove('skeleton');
        }
      };
      set('stat-stars', data.stargazers_count);
      set('stat-forks', data.forks_count);
      set('stat-issues', data.open_issues_count);
    } catch { /* silently fail */ }
  }
};

// Start app when DOM is ready
document.addEventListener('DOMContentLoaded', () => App.init());
