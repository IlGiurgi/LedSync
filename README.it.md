# LedSync

**Fa ballare le luci WLED con la musica che suona sul telefono Android.**
Spotify, YouTube, qualsiasi app: LedSync ascolta l'audio del telefono, lo analizza sul telefono e manda
lo spettacolo di luci ai WLED via Wi-Fi. Non serve un microfono sull'ESP32, né un PC, né LedFx.

## Funzioni
- **36 effetti** a tempo di musica calcolati sul telefono, molti adattati da [LedFx](https://github.com/LedFx/LedFx). Nel silenzio le luci si spengono, sui bassi colpiscono forte.
- **Più dispositivi insieme**, ognuno col suo numero di LED; per ciascuno: acceso/spento, effetto, direzione, nome.
- **Griglie di LED** (es. orologi a parole): colonne dell'equalizzatore, gli altri effetti come cerchi dal centro.
- **Yeelight** (Controllo LAN, modalità musica, ripiego automatico).
- **Govee** con LAN Control e **Philips Hue** tramite il bridge (un colore per luce).
- Luci **ESPHome** con l'effetto `wled` e ogni firmware che accetta il realtime UDP di WLED (DNRGB).
- **Modalità Sound Sync**: manda i pacchetti audio di WLED e usa gli effetti audio di WLED.
- 9 gradienti di colori, luminosità, specchio, sfocatura, rotazione colori, preferiti.
- Rilevamento del tempo (BPM) con effetti e colori agganciati al battito.
- Spegnimento automatico dopo un lungo silenzio; interfaccia in italiano e inglese.
- Cambio automatico dell'effetto a tempo, modalità microfono, notifica e riquadro nelle Impostazioni rapide.
- **Diagnostica di connessione e ritardi** integrata.
- Niente pubblicità, niente tracciamento, nessuna libreria Google, nessun accesso a Internet oltre la rete di casa.

## Requisiti
Android 10 o superiore; WLED 0.14+ sulla stessa rete Wi-Fi (oppure Yeelight / ESPHome).

## Compilare
`./gradlew assembleRelease` (oppure `tools/build-without-sdk.sh`).

## Licenza
GPL-3.0-or-later ([LICENSE](LICENSE)). Effetti adattati da LedFx (GPL-3.0), vedi [NOTICE.md](NOTICE.md).
Progetto indipendente, non affiliato a WLED, LedFx, Yeelight, Govee, Philips o ESPHome. Privacy: [PRIVACY.md](PRIVACY.md).
