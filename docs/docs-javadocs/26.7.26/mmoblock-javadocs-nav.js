(() => {
  const firstRow = document.querySelector('#navbar-top-firstrow')
  if (!firstRow || firstRow.querySelector('[data-mmoblock-nav]')) {
    return
  }

  const style = document.createElement('style')
  style.textContent = [
    '#navbar-top-firstrow .mmoblock-doc-link a { color: inherit; text-decoration: none; }',
    '#navbar-top-firstrow .mmoblock-doc-link a:hover { text-decoration: underline; }'
  ].join('')
  document.head.append(style)

  const links = [
    ['Documentation', '/'],
    ['GitHub', 'https://github.com/Rosaaalfi/MMOBlock-Rework']
  ]

  for (const [label, href] of links) {
    const item = document.createElement('li')
    item.className = 'mmoblock-doc-link'
    item.dataset.mmoblockNav = 'true'

    const anchor = document.createElement('a')
    anchor.href = href
    anchor.textContent = label
    if (href.startsWith('http')) {
      anchor.rel = 'noopener noreferrer'
      anchor.target = '_blank'
    }

    item.append(anchor)
    firstRow.append(item)
  }
})()