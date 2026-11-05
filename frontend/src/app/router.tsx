import { createBrowserRouter, type RouteObject } from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'
import { GuestOnly, RequireAuth } from '../features/auth/RequireAuth'
import { AcceptInvitationPage } from '../features/orgs/AcceptInvitationPage'
import { CreateOrganizationPage } from '../features/orgs/CreateOrganizationPage'
import { MembersPage } from '../features/orgs/MembersPage'
import { OrgIndexRedirect } from '../features/orgs/OrgIndexRedirect'
import { OrgLayout } from '../features/orgs/OrgLayout'
import { OrgOverviewPage } from '../features/orgs/OrgOverviewPage'
import { OrgSettingsPage } from '../features/orgs/OrgSettingsPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { AppLayout } from './AppLayout'

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        element: <GuestOnly />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          { index: true, element: <OrgIndexRedirect /> },
          { path: 'orgs/new', element: <CreateOrganizationPage /> },
          {
            path: 'orgs/:orgId',
            element: <OrgLayout />,
            children: [
              { index: true, element: <OrgOverviewPage /> },
              { path: 'members', element: <MembersPage /> },
              { path: 'settings', element: <OrgSettingsPage /> },
            ],
          },
          // The link in invitation emails (FLOWPILOT_INVITATION_ACCEPT_URL on the backend).
          { path: 'invitations/accept', element: <AcceptInvitationPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
