# eClaims Frontend - React Application

Modern React PWA for the eClaims insurance processing system.  
React 18 · TypeScript · Vite · TailwindCSS · Keycloak

---

## Quick Start

### Prerequisites

- **Node.js 20 LTS** or higher
- **npm** (comes with Node.js)
- Backend services running (see `../eclaims-backend/README.md`)

### Development Setup

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Open browser to http://localhost:5173
```

### Build Commands

```bash
# Type checking
npm run type-check

# Linting
npm run lint

# Production build
npm run build

# Preview production build
npm run preview
```

---

## Access URLs

| Environment | URL | Notes |
|-------------|-----|-------|
| **Development** | http://localhost:5173 | Vite dev server with hot reload |
| **Docker** | http://localhost:3000 | Nginx-served production build |
| **Preview** | http://localhost:4173 | Local production build preview |

---

## Demo Accounts

All accounts use password: **`Test@1234`**

| Username | Role | Portal Access |
|----------|------|---------------|
| `customer1` | Customer | Customer portal for claim submission and tracking |
| `surveyor1` | Surveyor | Internal portal for claim assessment |
| `adjustor1` | Adjustor | Internal portal for claim review and approval |
| `casemanager1` | Case Manager | Internal portal for case management |
| `auditor1` | Auditor | Internal portal for audit functions |
| `workshop1` | Workshop | Workshop portal for repair management |

---

## Architecture

### Tech Stack

- **React 18** with hooks and functional components
- **TypeScript** for type safety
- **Vite** for fast development and building
- **TailwindCSS** for styling
- **Keycloak-js** for authentication
- **React Router** for navigation
- **React Hook Form** with Zod validation
- **Tanstack Query** for server state management
- **Zustand** for client state management

### Project Structure

```
src/
├── components/         # Reusable UI components
├── pages/             # Route components
├── hooks/             # Custom React hooks
├── services/          # API calls and external services
├── stores/            # Zustand stores
├── types/             # TypeScript type definitions
├── utils/             # Helper functions
└── styles/            # Global styles and Tailwind config
```

### Authentication Flow

1. User visits any protected route
2. Redirected to Keycloak login if not authenticated
3. After login, JWT token is stored and used for API calls
4. Role-based routing shows appropriate portal interface

---

## Environment Configuration

### Development Environment Variables

Create a `.env` file in the frontend directory (not included in git):

```env
VITE_KEYCLOAK_URL=http://localhost:8080
VITE_KEYCLOAK_REALM=eclaims
VITE_KEYCLOAK_CLIENT_ID=eclaims-web
VITE_API_BASE_URL=http://localhost:8090/api/v1
```

### Docker Build Arguments

When building with Docker, these are passed as build arguments:

```dockerfile
ARG VITE_KEYCLOAK_URL=http://localhost:8080
ARG VITE_KEYCLOAK_REALM=eclaims
ARG VITE_KEYCLOAK_CLIENT_ID=eclaims-web
```

---

## Common Development Tasks

### Adding New Pages

1. Create component in `src/pages/`
2. Add route in `src/App.tsx`
3. Update navigation if needed

### Styling Guidelines

- Use TailwindCSS utility classes
- Create reusable components for common patterns
- Follow mobile-first responsive design
- Use consistent spacing and typography scales

### API Integration

- Use Tanstack Query for server state
- Define API functions in `src/services/`
- Handle loading, error, and success states
- Include proper TypeScript types

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `npm install` fails | Use Node.js 20 LTS; delete `node_modules` and retry |
| Port 5173 in use | Kill other Vite instances or change port in `vite.config.ts` |
| Authentication redirects fail | Check Keycloak is running on port 8080 |
| API calls fail | Ensure backend is running on port 8090 |
| TypeScript errors | Run `npm run type-check` for detailed diagnostics |
| Build fails | Check for missing environment variables |

### Development Tips

- Use browser dev tools for debugging
- Check Network tab for API call failures
- Use React Developer Tools extension
- Monitor console for errors and warnings
- Use `npm run lint` to catch code issues early

---

## PWA Features

The application is configured as a Progressive Web App (PWA):

- **Offline support** for cached pages
- **App-like experience** on mobile devices
- **Installation prompts** on supported browsers
- **Service worker** for background updates

To test PWA features:

1. Run `npm run build && npm run preview`
2. Open Chrome DevTools > Application > Service Workers
3. Test offline functionality
4. Use Lighthouse audit for PWA score

---

## Further Reading

- **Backend API documentation**: `../eclaims-backend/README.md`
- **Main setup guide**: `../README.md`
- **Vite documentation**: https://vitejs.dev/
- **React documentation**: https://react.dev/
- **TailwindCSS documentation**: https://tailwindcss.com/