/**
 * code-block.js — Transform .code-block divs into rich code blocks
 * with header (filename + copy button) and line numbers.
 */
const CodeBlock = {
  /**
   * Transform all .code-block elements on the page.
   * Safe to call multiple times — skips already-transformed blocks.
   */
  transform() {
    document.querySelectorAll('.code-block').forEach(block => {
      if (block.style.display === 'inline' || block.querySelector('.code-block-header')) return;

      const filename = block.getAttribute('data-filename') || 'code';
      const hasBtn = block.querySelector('.copy-btn');

      // Create header
      const header = document.createElement('div');
      header.className = 'code-block-header';
      header.innerHTML = '<span class="code-block-filename">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>' +
        filename + '</span>';

      let btn;
      if (hasBtn) {
        btn = block.querySelector('.copy-btn');
        btn.parentNode.removeChild(btn);
      } else {
        btn = document.createElement('button');
        btn.className = 'copy-btn';
        btn.textContent = 'copy';
      }
      header.appendChild(btn);

      // Build body with line numbers
      const body = document.createElement('div');
      body.className = 'code-block-body';

      const lines = document.createElement('div');
      lines.className = 'code-block-lines';

      const pre = document.createElement('pre');
      pre.className = 'code-block-content';

      // Move remaining children into pre, strip leading/trailing whitespace text nodes
      const frag = document.createDocumentFragment();
      const kids = Array.from(block.childNodes);
      let started = false;
      kids.forEach(child => {
        if (child === header || child === btn) return;
        if (!started && child.nodeType === 3 && child.textContent.trim() === '') return;
        if (child.nodeType === 1 || child.nodeType === 3) started = true;
        frag.appendChild(child);
      });
      // Strip trailing whitespace-only text nodes
      while (frag.lastChild && frag.lastChild.nodeType === 3 && frag.lastChild.textContent.trim() === '') {
        frag.removeChild(frag.lastChild);
      }
      pre.appendChild(frag);

      body.appendChild(lines);
      body.appendChild(pre);
      block.insertBefore(header, block.firstChild);
      block.appendChild(body);

      // Count lines
      const html = pre.innerHTML;
      let lineCount = 1;
      if (html.indexOf('<br') !== -1) {
        lineCount = (html.match(/<br\s*\/?>/gi) || []).length + 1;
      } else {
        const segs = html.split('\n');
        while (segs.length > 0 && segs[0].trim() === '') segs.shift();
        while (segs.length > 0 && segs[segs.length - 1].trim() === '') segs.pop();
        lineCount = Math.max(1, segs.length);
      }

      for (let i = 1; i <= lineCount; i++) {
        const sp = document.createElement('span');
        sp.textContent = i;
        lines.appendChild(sp);
      }
    });

    // Wire up copy buttons
    document.querySelectorAll('.copy-btn').forEach(btn => {
      if (btn.dataset.bound) return;
      btn.dataset.bound = '1';
      btn.addEventListener('click', function () {
        const block = this.closest('.code-block');
        const content = block ? block.querySelector('.code-block-content') : null;
        const text = content ? content.textContent.trim() : this.parentElement.nextElementSibling?.textContent?.trim() || '';
        navigator.clipboard.writeText(text).then(() => {
          this.textContent = 'copied!';
          setTimeout(() => { this.textContent = 'copy'; }, 1500);
        });
      });
    });
  },

  /**
   * Switch dependency tab (Gradle/Maven).
   */
  switchDep(type, btn) {
    const gradle = document.getElementById('dep-gradle');
    const maven = document.getElementById('dep-maven');
    if (gradle) gradle.style.display = type === 'gradle' ? 'block' : 'none';
    if (maven) maven.style.display = type === 'maven' ? 'block' : 'none';
    document.querySelectorAll('.dep-tab').forEach(t => t.classList.remove('active'));
    if (btn) btn.classList.add('active');
  },

  /**
   * Copy code from a named dependency block.
   */
  copyCode(id) {
    const el = document.getElementById(id);
    if (!el) return;
    const content = el.querySelector('.code-block-content');
    const text = content ? content.textContent.trim() : el.innerText.replace(/copy$/, '').trim();
    navigator.clipboard.writeText(text).then(() => {
      const btn = el.querySelector('.copy-btn');
      if (btn) {
        btn.textContent = 'copied!';
        setTimeout(() => { btn.textContent = 'copy'; }, 1500);
      }
    });
  }
};

// Make switchDep and copyCode globally accessible for inline onclick handlers
window.switchDep = (type, btn) => CodeBlock.switchDep(type, btn);
window.copyCode = (id) => CodeBlock.copyCode(id);
