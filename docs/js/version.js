/**
 * version.js — Fetch latest version from Chyxel Maven repository
 * Uses maven-metadata.xml to get the latest release version.
 */
const Version = {
  REPO_URL: 'https://public-repo.chyxelmc.me/repository',
  GROUP_PATH: 'me/chyxelmc/mmoblock-api',
  ARTIFACT: 'mmoblock-api',

  /**
   * Fetch latest version from Maven metadata XML.
   * @returns {Promise<string|null>}
   */
  async fetchLatest() {
    try {
      // Using AllOrigins as the proxy
      const targetUrl = `${this.REPO_URL}/${this.GROUP_PATH}/maven-metadata.xml`;
      const url = `https://api.allorigins.win/raw?url=${encodeURIComponent(targetUrl)}`;
      const res = await fetch(url);
      if (!res.ok) return null;
      const xml = await res.text();
      // Parse <release> tag from maven-metadata.xml
      const match = xml.match(/<release>([^<]+)<\/release>/);
      if (match && match[1]) return match[1];
      // Fallback to <latest> tag
      const latestMatch = xml.match(/<latest>([^<]+)<\/latest>/);
      return latestMatch ? latestMatch[1] : null;
    } catch {
      return null;
    }
  },

  /**
   * Replace all {{VERSION}} placeholders in the DOM with the given version.
   * @param {string} version
   */
  replacePlaceholders(version) {
    if (!version) return;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
    let node;
    const nodes = [];
    while ((node = walker.nextNode())) nodes.push(node);
    nodes.forEach(n => {
      if (n.nodeValue.includes('{{VERSION}}')) {
        n.nodeValue = n.nodeValue.split('{{VERSION}}').join(version);
      }
    });
  },

  /**
   * Initialize: fetch version and replace placeholders.
   * Also updates stat-version element if present.
   */
  async init() {
    const ver = await this.fetchLatest();
    if (ver) {
      const statEl = document.getElementById('stat-version');
      if (statEl) statEl.textContent = ver;
      this.replacePlaceholders(ver);
    }
  }
};
