import { createSlice } from "@reduxjs/toolkit";

const notificationsSlice = createSlice({
  name: "notifications",
  initialState: { items: [], unreadCount: 0 },
  reducers: {
    setNotifications: (state, action) => {
      state.items = action.payload;
      state.unreadCount = action.payload.filter(n => !n.read).length;
    },
    markRead: (state, action) => {
      const n = state.items.find(i => i.id === action.payload);
      if (n) { n.read = true; state.unreadCount = Math.max(0, state.unreadCount - 1); }
    },
  },
});

export const { setNotifications, markRead } = notificationsSlice.actions;
export default notificationsSlice.reducer;
