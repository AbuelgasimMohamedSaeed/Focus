// Bridge for the little top-of-screen pill.
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('island', {
  onState: cb => ipcRenderer.on('island:state', (_e, s) => cb(s)),
  open: () => ipcRenderer.send('island:open'),
  close: () => ipcRenderer.send('island:close'),               // hide for this sit
  collapse: on => ipcRenderer.send('island:collapse', !!on),   // line-only mode
  mouse: over => ipcRenderer.send('island:mouse', !!over)      // clicks pass through except on the pill/line
});
