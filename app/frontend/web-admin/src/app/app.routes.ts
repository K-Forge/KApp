import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

// Grows one feature at a time as each is built (see AGENTS commit history) - a route is only
// added here once the component behind it exists, so the app always builds at every commit.
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'my-token' },
      {
        path: 'my-token',
        loadComponent: () => import('./features/identity/identity.page').then((m) => m.IdentityPage),
      },
      {
        path: 'api-console',
        loadComponent: () => import('./features/console/console.page').then((m) => m.ConsolePage),
      },
      {
        path: 'data/users',
        loadComponent: () => import('./features/data/users/users.page').then((m) => m.UsersPage),
      },
      {
        path: 'data/buildings',
        loadComponent: () => import('./features/data/buildings/buildings.page').then((m) => m.BuildingsPage),
      },
      {
        path: 'data/spaces',
        loadComponent: () => import('./features/data/spaces/spaces.page').then((m) => m.SpacesPage),
      },
      {
        path: 'data/floors',
        loadComponent: () => import('./features/data/floors/floors.page').then((m) => m.FloorsPage),
      },
      {
        path: 'data/floors/:building/:floor',
        loadComponent: () => import('./features/data/floors/floor-editor.page').then((m) => m.FloorEditorPage),
        // Unsaved changes survive leaving - they are kept on the device - but leaving without
        // noticing them is how a floor stays unsaved for a week.
        canDeactivate: [(page: { canLeave(): boolean }) => page.canLeave()],
      },
      {
        path: 'data/space-types',
        loadComponent: () => import('./features/data/spaces/space-types.page').then((m) => m.SpaceTypesPage),
      },
      {
        path: 'data/programs',
        loadComponent: () => import('./features/data/programs/programs.page').then((m) => m.ProgramsPage),
      },
      {
        path: 'data/pensums',
        loadComponent: () => import('./features/data/pensums/pensums.page').then((m) => m.PensumsPage),
      },
      // The screen, the API and the classes behind them were all called "curricula" until
      // somebody pointed out that nobody in the building says that - it is a pensum. Kept as a
      // redirect so a link somebody already bookmarked still lands somewhere.
      { path: 'data/curricula', redirectTo: 'data/pensums' },
      { path: 'identity', redirectTo: 'my-token' },
      { path: 'roles', redirectTo: 'who-can-do-what' },
      { path: 'console', redirectTo: 'api-console' },
      {
        path: 'data/invitation-codes',
        loadComponent: () =>
          import('./features/data/invitation-codes/invitation-codes.page').then((m) => m.InvitationCodesPage),
      },
      {
        path: 'data/visitor-passes',
        loadComponent: () =>
          import('./features/data/visitor-passes/visitor-passes.page').then((m) => m.VisitorPassesPage),
      },
      // Importing is not its own screen any more; it is the create half of Pensums.
      { path: 'data/import', redirectTo: 'data/pensums' },
      {
        path: 'who-can-do-what',
        loadComponent: () => import('./features/roles/role-inspector.page').then((m) => m.RoleInspectorPage),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
