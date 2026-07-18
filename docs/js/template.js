/**
 * template.js — Component loader
 * Fetches HTML component files and injects them into the DOM.
 */
const Template = {
  /**
   * Fetch a component HTML file and return its content as text.
   * @param {string} path - Path relative to docs/ root
   * @returns {Promise<string>}
   */
  async fetch(path) {
    const res = await fetch(path);
    if (!res.ok) throw new Error(`Failed to load component: ${path}`);
    return res.text();
  },

  /**
   * Load a component and insert it into a target element.
   * @param {string} path - Component file path
   * @param {string|Element} target - CSS selector or Element to insert into
   * @param {'inner'|'outer'|'beforeend'} mode - How to insert
   */
  async load(path, target, mode = 'inner') {
    const html = await this.fetch(path);
    const el = typeof target === 'string' ? document.querySelector(target) : target;
    if (!el) throw new Error(`Target not found: ${target}`);
    if (mode === 'inner') el.innerHTML = html;
    else if (mode === 'outer') el.outerHTML = html;
    else if (mode === 'beforeend') el.insertAdjacentHTML('beforeend', html);
    return el;
  },

  /**
   * Load multiple components in parallel.
   * @param {Array<{path: string, target: string, mode?: string}>} items
   */
  async loadAll(items) {
    return Promise.all(items.map(item =>
      this.load(item.path, item.target, item.mode || 'inner')
    ));
  }
};
