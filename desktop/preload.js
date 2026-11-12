// The only bridge between the page and Windows.
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('stillNative', {
  mini: on => ipcRenderer.invoke('still:mini', !!on),          // mini mode on/off
  saveBackup: json => ipcRenderer.invoke('still:backup', json), // automatic backup
  findBackup: () => ipcRenderer.invoke('still:findBackup'),     // newest backup, if any
  sitState: s => ipcRenderer.invoke('still:sit', s)             // running sit, for the closed-window notification
});
