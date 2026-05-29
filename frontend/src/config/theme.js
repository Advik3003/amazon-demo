/**
 * THEME CONFIGURATION
 * ====================
 * Centralized theme management.
 * Supports: Dark/Light mode + Multiple color themes.
 *
 * TO ADD A NEW THEME:
 * 1. Add a new entry to the THEMES object below
 * 2. It automatically appears in the theme selector
 *
 * CURRENT THEMES:
 * - amazon (default): Amazon-inspired orange/black
 * - blue: Professional blue corporate
 * - green: Eco-friendly green
 * - purple: Modern purple
 */

import { createTheme } from "@mui/material/styles";

// ==================== THEME DEFINITIONS ====================

export const THEME_CONFIGS = {
  amazon: {
    id: "amazon",
    name: "Amazon Classic",
    light: {
      primary: { main: "#FF9900", dark: "#E68A00", light: "#FFB84D", contrastText: "#000" },
      secondary: { main: "#146EB4", contrastText: "#fff" },
      background: { default: "#EAEDED", paper: "#FFFFFF" },
    },
    dark: {
      primary: { main: "#FF9900", dark: "#E68A00", light: "#FFB84D", contrastText: "#000" },
      secondary: { main: "#2196F3", contrastText: "#fff" },
      background: { default: "#131921", paper: "#1E2A38" },
    },
  },
  blue: {
    id: "blue",
    name: "Ocean Blue",
    light: {
      primary: { main: "#1976D2", dark: "#115293", light: "#4791DB", contrastText: "#fff" },
      secondary: { main: "#F50057", contrastText: "#fff" },
      background: { default: "#F5F7FA", paper: "#FFFFFF" },
    },
    dark: {
      primary: { main: "#90CAF9", dark: "#42A5F5", light: "#E3F2FD", contrastText: "#000" },
      secondary: { main: "#F48FB1", contrastText: "#000" },
      background: { default: "#0A1929", paper: "#0D2137" },
    },
  },
  green: {
    id: "green",
    name: "Eco Green",
    light: {
      primary: { main: "#388E3C", dark: "#1B5E20", light: "#66BB6A", contrastText: "#fff" },
      secondary: { main: "#FF6F00", contrastText: "#fff" },
      background: { default: "#F1F8E9", paper: "#FFFFFF" },
    },
    dark: {
      primary: { main: "#4CAF50", dark: "#388E3C", light: "#81C784", contrastText: "#000" },
      secondary: { main: "#FFA000", contrastText: "#000" },
      background: { default: "#0A1F0A", paper: "#0D2B0D" },
    },
  },
  purple: {
    id: "purple",
    name: "Royal Purple",
    light: {
      primary: { main: "#7B1FA2", dark: "#4A0072", light: "#AE52D4", contrastText: "#fff" },
      secondary: { main: "#FF6D00", contrastText: "#fff" },
      background: { default: "#F3E5F5", paper: "#FFFFFF" },
    },
    dark: {
      primary: { main: "#CE93D8", dark: "#AB47BC", light: "#E1BEE7", contrastText: "#000" },
      secondary: { main: "#FFAB40", contrastText: "#000" },
      background: { default: "#12005E", paper: "#1A0080" },
    },
  },
};

/**
 * Create a Material UI theme from theme config and mode
 * @param {string} themeId - Theme identifier (amazon, blue, green, purple)
 * @param {string} mode - 'light' or 'dark'
 */
export const createAppTheme = (themeId = "amazon", mode = "light") => {
  const config = THEME_CONFIGS[themeId] || THEME_CONFIGS.amazon;
  const palette = mode === "dark" ? config.dark : config.light;

  return createTheme({
    palette: {
      mode,
      ...palette,
    },
    typography: {
      fontFamily: '"Amazon Ember", "Helvetica Neue", Roboto, Arial, sans-serif',
      h1: { fontWeight: 700 },
      h2: { fontWeight: 600 },
      h3: { fontWeight: 600 },
      button: { textTransform: "none", fontWeight: 600 },
    },
    shape: {
      borderRadius: 4,
    },
    components: {
      MuiButton: {
        styleOverrides: {
          containedPrimary: {
            background: `linear-gradient(to bottom, ${palette.primary.light}, ${palette.primary.main})`,
            boxShadow: "0 1px 0 rgba(255,255,255,0.4) inset, 0 1px 0 rgba(0,0,0,0.2)",
            "&:hover": {
              background: `linear-gradient(to bottom, ${palette.primary.main}, ${palette.primary.dark})`,
            },
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            boxShadow: "0 1px 4px rgba(0,0,0,0.1)",
            "&:hover": {
              boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
            },
          },
        },
      },
    },
  });
};
