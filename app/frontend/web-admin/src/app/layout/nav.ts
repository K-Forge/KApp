/** The portal's sections, as the sidebar lists them: also where a page finds its own icon. */
export interface NavLink {
  path: string;
  label: string;
  /** Inline SVG path data, 24x24. Emoji render differently on every platform and read as
      decoration; a stroked glyph reads as an icon and inherits the current text colour. */
  icon: string;
}

export interface NavGroup {
  title: string;
  links: NavLink[];
}

// Grouped because eleven flat entries is a list to read, not a menu to use. The three groups
// are the three reasons somebody opens this portal: to check who they are, to look after the
// data, or to inspect how the API behaves.
export const NAV_GROUPS: NavGroup[] = [
  {
    title: /* i18n */ 'Academic',
    links: [
      { path: '/data/programs', label: /* i18n */ 'Programs', icon: 'M3 7l9-4 9 4-9 4-9-4zm0 5l9 4 9-4M3 17l9 4 9-4' },
      { path: '/data/pensums', label: /* i18n */ 'Pensums', icon: 'M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2zM8 7h8M8 11h8M8 15h5' },
    ],
  },
  {
    title: /* i18n */ 'Campus',
    links: [
      { path: '/data/campus', label: /* i18n */ 'Campus map', icon: 'M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2zM9 4v14M15 6v14' },
      { path: '/data/survey', label: /* i18n */ 'Survey', icon: 'M3 17 17 3l4 4L7 21zM7 13l2 2M10 10l2 2M13 7l2 2' },
      { path: '/data/buildings', label: /* i18n */ 'Buildings', icon: 'M3 21h18M5 21V5a2 2 0 012-2h6a2 2 0 012 2v16M9 7h2M9 11h2M9 15h2M15 21v-8h4v8' },
      { path: '/data/floors', label: /* i18n */ 'Floor editor', icon: 'M3 3h18v18H3zM3 9h18M9 9v12M15 15h6' },
      { path: '/data/spaces', label: /* i18n */ 'Spaces', icon: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z' },
    ],
  },
  {
    title: /* i18n */ 'People and access',
    links: [
      { path: '/data/users', label: /* i18n */ 'Users', icon: 'M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM22 21v-2a4 4 0 00-3-3.87' },
      { path: '/data/invitation-codes', label: /* i18n */ 'Invitation codes', icon: 'M15 7a4 4 0 11-5.66 5.66L3 19v2h2l6.34-6.34A4 4 0 0115 7zM16 8h.01' },
      { path: '/data/visitor-passes', label: /* i18n */ 'Visitor passes', icon: 'M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2zM3 11h18M7 15h4' },
    ],
  },
  {
    title: /* i18n */ 'Inspect',
    links: [
      { path: '/my-token', label: /* i18n */ 'My token', icon: 'M12 2l8 4v6c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6l8-4zM9 12l2 2 4-4' },
      { path: '/who-can-do-what', label: /* i18n */ 'Who can do what', icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11' },
      { path: '/api-console', label: /* i18n */ 'API console', icon: 'M8 9l-4 3 4 3M16 9l4 3-4 3M13 5l-2 14' },
    ],
  },
];

/** The sidebar entry a page lives under, by its address: the longest path that starts it. */
export function navEntryFor(url: string): { group: NavGroup; link: NavLink } | null {
  const path = url.split(/[?#]/)[0];
  let best: { group: NavGroup; link: NavLink } | null = null;
  for (const group of NAV_GROUPS) {
    for (const link of group.links) {
      if ((path === link.path || path.startsWith(link.path + '/')) && (!best || link.path.length > best.link.path.length)) {
        best = { group, link };
      }
    }
  }
  return best;
}
