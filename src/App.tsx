import { SequenceConnect } from "@0xsequence/connect";
import { config } from "./config";

import { useAccount, useDisconnect, useSwitchChain } from "wagmi";
import { NotConnected } from "./views/NotConnected";
import { Dashboard } from "./views/Dashboard";
import { SequenceBoilerplate } from "@0xsequence-demos/boilerplate-design-system";
import { useState } from "react";

export default function Layout() {
  return (
    <SequenceConnect config={config}>
      <App />
    </SequenceConnect>
  );
}

function App() {
  const { isConnected } = useAccount();
  const [showDashboard, setShowDashboard] = useState(true);
  
  if (isConnected && showDashboard) {
    return (
      <div style={{ position: 'relative' }}>
        <button
          onClick={() => setShowDashboard(false)}
          style={{
            position: 'fixed',
            top: '20px',
            right: '20px',
            background: 'rgba(255, 255, 255, 0.2)',
            border: '1px solid rgba(255, 255, 255, 0.3)',
            borderRadius: '8px',
            padding: '10px 20px',
            color: 'white',
            cursor: 'pointer',
            zIndex: 1000,
            fontSize: '14px',
            fontWeight: '600',
            backdropFilter: 'blur(10px)'
          }}
        >
          🔧 Dev Mode
        </button>
        <Dashboard />
      </div>
    );
  }
  
  return (
    <SequenceBoilerplate
      githubUrl="https://github.com/0xsequence-demos/kit-embedded-wallet-react-boilerplate"
      name="CcOp Synergy Wallet"
      description="Embedded Wallet Dashboard"
      wagmi={{ useAccount, useDisconnect, useSwitchChain }}
    >
      {isConnected && (
        <button
          onClick={() => setShowDashboard(true)}
          style={{
            position: 'fixed',
            top: '20px',
            right: '20px',
            background: 'linear-gradient(45deg, #667eea, #764ba2)',
            border: 'none',
            borderRadius: '8px',
            padding: '10px 20px',
            color: 'white',
            cursor: 'pointer',
            zIndex: 1000,
            fontSize: '14px',
            fontWeight: '600'
          }}
        >
          📊 Dashboard
        </button>
      )}
      <NotConnected />
    </SequenceBoilerplate>
  );
}
