import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Served as a GitHub Pages project site at /<repo-name>/ (same repo name on
// the fork and on epam/dm.ai), so assets must resolve under that base path.
export default defineConfig({
  base: '/dm.ai/',
  plugins: [react()],
})
