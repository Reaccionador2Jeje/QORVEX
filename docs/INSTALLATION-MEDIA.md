# Installation media

QORVEX uses Android's Storage Access Framework through `ACTION_OPEN_DOCUMENT` for ISO selection. The app requests read access and persists the returned URI permission so the selected document can remain available after the picker closes and after app restarts.

The selected URI is stored in the VM profile. QORVEX does not copy large ISO files automatically, avoiding unexpected storage use. A provider can revoke access or remove a document; callers must handle read failures when the real QEMU block backend is added.

The current UI records the selected ISO but cannot boot it because QEMU is not yet integrated.
