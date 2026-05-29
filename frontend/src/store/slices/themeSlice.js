import { createSlice } from "@reduxjs/toolkit";

const themeSlice = createSlice({
  name: "theme",
  initialState: {
    mode: localStorage.getItem("themeMode") || "light",
    colorTheme: localStorage.getItem("colorTheme") || "amazon",
  },
  reducers: {
    toggleDarkMode: (state) => {
      state.mode = state.mode === "light" ? "dark" : "light";
      localStorage.setItem("themeMode", state.mode);
    },
    setColorTheme: (state, action) => {
      state.colorTheme = action.payload;
      localStorage.setItem("colorTheme", action.payload);
    },
  },
});

export const { toggleDarkMode, setColorTheme } = themeSlice.actions;
export default themeSlice.reducer;
