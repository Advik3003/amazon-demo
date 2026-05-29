/**
 * APP CONFIG - Central Branding Configuration
 * =============================================
 * Change app name, logo, colors, and API URL from ONE place.
 * This enables white-label / easy rebranding.
 *
 * HOW TO REBRAND:
 * 1. Change APP_NAME below
 * 2. Replace logo.png in /public/assets/
 * 3. Update theme in theme.js
 * That's it! The entire app uses these values.
 */

const appConfig = {
  // ==================== BRANDING ====================
  APP_NAME: "Amazon Demo",
  APP_TAGLINE: "Everything you need, delivered fast",
  COMPANY_NAME: "Amazon Demo Inc.",
  SUPPORT_EMAIL: "support@amazondemo.com",
  LOGO_URL: "/assets/logo.png",
  FAVICON_URL: "/assets/favicon.ico",

  // ==================== API ====================
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  API_TIMEOUT: 10000,

  // ==================== FEATURE FLAGS ====================
  FEATURES: {
    DARK_MODE: true,
    MULTI_THEME: true,
    WISHLIST: true,
    REVIEWS: true,
    NOTIFICATIONS: true,
    ANALYTICS: false, // Enable in production
  },

  // ==================== PAGINATION ====================
  DEFAULT_PAGE_SIZE: 20,
  MAX_PAGE_SIZE: 100,

  // ==================== CURRENCY ====================
  CURRENCY: "USD",
  CURRENCY_SYMBOL: "$",
  LOCALE: "en-US",
};

export default appConfig;
