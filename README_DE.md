# CcOp React Wallet - Sequence Integration

## 🚀 Übersicht

React-Anwendung mit Sequence Embedded Wallet-Integration für das CcOp-System. Ermöglicht User-Login und Wallet-Management über mehrere Blockchain-Netzwerke.

## ✨ Features

- ✅ **Sequence Embedded Wallet** - Vollständige Wallet-Integration
- ✅ **Multi-Chain Support** - 50+ unterstützte Blockchain-Netzwerke
- ✅ **Social Login** - Google, Apple, Email-Authentifizierung
- ✅ **WalletConnect** - Verbindung mit externen Wallets
- ✅ **MetaMask & Coinbase** - Browser-Extension-Support
- ✅ **Dark Theme** - Moderne UI mit dunklem Design

## 🛠️ Installation & Start

### 1. Dependencies installieren
```bash
npm install
```

### 2. Konfiguration anpassen
Die `.env`-Datei ist bereits konfiguriert mit:
- ✅ CcOp Project Access Key
- ✅ WaaS Config Key
- ✅ Multi-Chain-Konfiguration
- ⚠️ Benötigt: Google/Apple Client IDs (optional)

### 3. Development Server starten
```bash
npm run dev
```

Die Anwendung läuft auf: `http://localhost:5173`

### 4. Production Build
```bash
npm run build
npm run preview
```

## 🔧 Konfiguration

### Unterstützte Netzwerke
```
Testnets:
- Telos Testnet (Chain ID: 41)
- BSC Testnet
- Moonbase Alpha
- Arbitrum Sepolia
- Base Sepolia
- und 25+ weitere...

Mainnets:
- Polygon
- Arbitrum
- Optimism
- Ethereum Mainnet
- BSC
- Avalanche
- und 20+ weitere...
```

### Environment Variables
```env
VITE_PROJECT_ACCESS_KEY       # Sequence Project Access Key
VITE_WAAS_CONFIG_KEY          # Wallet-as-a-Service Config
VITE_DEFAULT_CHAIN=41         # Telos Testnet
VITE_DEFAULT_THEME=dark       # UI Theme
VITE_PROJECT_NAME=synergy     # Projekt-Name
```

## 📁 Projekt-Struktur

```
websdk-embedded-wallet-react-boilerplate/
├── src/
│   ├── main.tsx              # App Entry Point
│   ├── App.tsx               # Hauptkomponente
│   ├── components/           # React-Komponenten
│   └── utils/                # Hilfsfunktionen
├── public/                   # Statische Assets
├── .env                      # Konfiguration
├── package.json              # Dependencies
└── vite.config.ts           # Vite-Konfiguration
```

## 🔗 Integration mit CcOp

Diese Wallet-Anwendung kann integriert werden mit:
- 🎯 [CcOp User Dashboard](../Landingpages/ccop-user-dashboard.html)
- 🤖 [CcOp Social Media Bot](../CCOP Social Media Bot/)
- 🎨 CcOp NFT App
- 📊 CcOp Analytics Dashboard

## 🎨 Anpassung

### Theme anpassen
In `.env`:
```env
VITE_DEFAULT_THEME=dark  # oder 'light'
VITE_POSITION=center     # oder 'top-center', 'bottom-right', etc.
```

### Chains hinzufügen/entfernen
Liste in `VITE_CHAINS` bearbeiten (kommagetrennt)

## 📚 Sequence Documentation

- [Sequence Docs](https://docs.sequence.xyz/)
- [React Integration Guide](https://docs.sequence.xyz/sdk/embedded-wallet/examples/react)
- [API Reference](https://docs.sequence.xyz/api/authentication)

## 🔐 Sicherheit

**Wichtig:**
- ❌ Niemals Private Keys committen
- ✅ `.env` ist in `.gitignore`
- ✅ Verwende Environment Variables für sensible Daten
- ✅ HTTPS in Production

## 🐛 Troubleshooting

### Port bereits belegt
```bash
# Verwende anderen Port
npm run dev -- --port 3000
```

### Dependencies-Fehler
```bash
# Cache löschen
rm -rf node_modules package-lock.json
npm install
```

### Wallet verbindet nicht
1. Überprüfe `VITE_PROJECT_ACCESS_KEY`
2. Stelle sicher, dass die Chain ID valide ist
3. Prüfe Browser-Console auf Fehler

## 📝 Scripts

```bash
npm run dev          # Development Server
npm run build        # Production Build
npm run preview      # Preview Production Build
npm run lint         # Code Linting
npm run lint:fix     # Auto-Fix Linting Issues
```

## 🚀 Nächste Schritte

1. ✅ Projekt läuft lokal
2. 📱 Teste Wallet-Verbindung
3. 🎨 Passe Design an
4. 🔗 Integriere mit CcOp Backend
5. 🚀 Deploy auf Production

## 📞 Support

- 📧 GitHub Issues
- 📖 Sequence Discord
- 📝 [CcOp Documentation](../docs/)

---

**Version:** 1.0.0  
**Status:** ✅ Ready for Development  
**Erstellt:** 24. Januar 2026
