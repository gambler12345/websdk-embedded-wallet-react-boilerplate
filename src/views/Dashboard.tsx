import { useAccount, useBalance, useChainId, useSwitchChain } from 'wagmi'
import { useState, useEffect } from 'react'
import { formatUnits } from 'ethers'

interface Token {
  name: string
  symbol: string
  balance: string
  value: string
  address: string
  decimals?: number
}

interface Transaction {
  hash: string
  type: string
  amount: string
  date: string
  status: string
}

export function Dashboard() {
  const { address, isConnected } = useAccount()
  const { data: balance } = useBalance({ address })
  const chainId = useChainId()
  const { switchChain } = useSwitchChain()
  const [activeTab, setActiveTab] = useState<'overview' | 'portfolio' | 'transactions'>('overview')
  const [tokens, setTokens] = useState<Token[]>([])
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(false)
  const [loadingTx, setLoadingTx] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Network names mapping
  const networkNames: Record<number, string> = {
    1: 'Ethereum',
    137: 'Polygon',
    41: 'Telos Testnet',
    56: 'BSC',
    42161: 'Arbitrum',
    10: 'Optimism',
    8453: 'Base'
  }

  // Helper function to format transaction hash
  const formatHash = (hash: string) => {
    return `${hash.substring(0, 10)}...${hash.substring(hash.length - 8)}`
  }

  // Helper function to copy to clipboard
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    alert('Copied to clipboard!')
  }

  useEffect(() => {
    const fetchTokenBalances = async () => {
      if (!isConnected || !address) return

      setLoading(true)
      setError(null)

      try {
        // Start with native token from wagmi
        const tokenList: Token[] = []
        
        if (balance) {
          tokenList.push({
            name: balance.symbol || 'Native Token',
            symbol: balance.symbol || 'TOKEN',
            balance: balance.formatted || '0.00',
            value: '$0.00',
            address: '0x0000000000000000000000000000000000000000',
            decimals: balance.decimals
          })
        }

        // Get explorer API endpoint and key based on chain
        const getExplorerConfig = (chainId: number) => {
          switch (chainId) {
            case 137: 
              return { 
                url: 'https://api.polygonscan.com/api',
                key: import.meta.env.VITE_POLYGONSCAN_API_KEY || ''
              }
            case 1: 
              return { 
                url: 'https://api.etherscan.io/api',
                key: import.meta.env.VITE_ETHERSCAN_API_KEY || ''
              }
            case 56: 
              return { 
                url: 'https://api.bscscan.com/api',
                key: import.meta.env.VITE_BSCSCAN_API_KEY || ''
              }
            case 42161: 
              return { 
                url: 'https://api.arbiscan.io/api',
                key: ''
              }
            case 10: 
              return { 
                url: 'https://api-optimistic.etherscan.io/api',
                key: ''
              }
            case 8453: 
              return { 
                url: 'https://api.basescan.org/api',
                key: ''
              }
            default: return null
          }
        }

        const explorerConfig = getExplorerConfig(chainId)

        // Always use Sequence API as primary method (more reliable without API keys)
        try {
          console.log('Fetching tokens from Sequence Indexer API...')
          const response = await fetch(`https://api.sequence.app/rpc/Indexer/GetTokenBalances`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'X-Access-Key': import.meta.env.VITE_PROJECT_ACCESS_KEY || ''
            },
            body: JSON.stringify({
              chainID: chainId.toString(),
              accountAddress: address,
              includeMetadata: true
            })
          })

          if (response.ok) {
            const data = await response.json()
            console.log('Sequence API response:', data)
            
            if (data.balances && Array.isArray(data.balances)) {
              data.balances.forEach((token: any) => {
                if (token.contractAddress && token.balance !== '0') {
                  const decimals = token.tokenMetadata?.decimals || 18
                  const formattedBalance = formatUnits(token.balance, decimals)
                  
                  tokenList.push({
                    name: token.tokenMetadata?.name || 'Unknown Token',
                    symbol: token.tokenMetadata?.symbol || '???',
                    balance: parseFloat(formattedBalance).toFixed(6),
                    value: '$0.00',
                    address: token.contractAddress,
                    decimals: decimals
                  })
                }
              })
              console.log(`Found ${tokenList.length - 1} ERC20 tokens via Sequence API`)
            }
          } else {
            console.error('Sequence API failed:', response.status, response.statusText)
          }
        } catch (sequenceError) {
          console.error('Sequence API error:', sequenceError)
        }

        // If Sequence API didn't return many tokens, try Explorer API as fallback
        if (tokenList.length <= 1 && explorerConfig) {
          console.log('Trying Explorer API fallback...')
          try {
            const apiKey = explorerConfig.key ? `&apikey=${explorerConfig.key}` : ''
            const tokenBalanceUrl = `${explorerConfig.url}?module=account&action=tokentx&address=${address}&page=1&offset=100&sort=desc${apiKey}`
            
            console.log('Fetching from:', explorerConfig.url)
            const tokenBalanceResponse = await fetch(tokenBalanceUrl)

            if (tokenBalanceResponse.ok) {
              const tokenData = await tokenBalanceResponse.json()
              console.log('Explorer API response:', tokenData)
              
              if (tokenData.status === '1' && tokenData.result && Array.isArray(tokenData.result)) {
                const uniqueTokens = new Map<string, any>()
                
                tokenData.result.forEach((tx: any) => {
                  const tokenAddress = tx.contractAddress.toLowerCase()
                  if (!uniqueTokens.has(tokenAddress)) {
                    uniqueTokens.set(tokenAddress, {
                      address: tx.contractAddress,
                      name: tx.tokenName,
                      symbol: tx.tokenSymbol,
                      decimals: parseInt(tx.tokenDecimal) || 18
                    })
                  }
                })

                console.log(`Found ${uniqueTokens.size} unique tokens from transaction history`)

                for (const [_, token] of uniqueTokens) {
                  try {
                    const balanceUrl = `${explorerConfig.url}?module=account&action=tokenbalance&contractaddress=${token.address}&address=${address}&tag=latest${apiKey}`
                    const balanceResponse = await fetch(balanceUrl)
                    
                    if (balanceResponse.ok) {
                      const balanceData = await balanceResponse.json()
                      
                      if (balanceData.status === '1' && balanceData.result !== '0') {
                        const formattedBalance = formatUnits(balanceData.result, token.decimals)
                        const balanceNum = parseFloat(formattedBalance)
                        
                        if (balanceNum > 0) {
                          // Check if this token is already in the list
                          const exists = tokenList.some(t => t.address.toLowerCase() === token.address.toLowerCase())
                          if (!exists) {
                            tokenList.push({
                              name: token.name || 'Unknown Token',
                              symbol: token.symbol || '???',
                              balance: balanceNum.toFixed(6),
                              value: '$0.00',
                              address: token.address,
                              decimals: token.decimals
                            })
                          }
                        }
                      }
                    }
                    
                    await new Promise(resolve => setTimeout(resolve, 250))
                  } catch (err) {
                    console.error(`Error fetching balance for token ${token.symbol}:`, err)
                  }
                }
              } else if (tokenData.status === '0') {
                console.warn('Explorer API returned error:', tokenData.message)
                if (tokenData.message?.includes('API Key')) {
                  setError(`⚠️ ${explorerConfig.url.includes('polygon') ? 'Polygonscan' : 'Explorer'} API Key benötigt für vollständige Token-Liste`)
                }
              }
            }
          } catch (explorerError) {
            console.error('Explorer API error:', explorerError)
          }
        }

        setTokens(tokenList)
        
        // Fetch transaction history
        await fetchTransactionHistory()
      } catch (err) {
        console.error('Error fetching token balances:', err)
        setError('Fehler beim Laden der Token-Daten')
        
        // Fallback: show only native token
        if (balance) {
          setTokens([{
            name: balance.symbol || 'Native Token',
            symbol: balance.symbol || 'TOKEN',
            balance: balance.formatted || '0.00',
            value: '$0.00',
            address: '0x0000000000000000000000000000000000000000',
            decimals: balance.decimals
          }])
        }
      } finally {
        setLoading(false)
      }
    }

    fetchTokenBalances()
  }, [isConnected, address, balance, chainId])

  const fetchTransactionHistory = async () => {
    if (!address) return
    
    setLoadingTx(true)
    try {
      const response = await fetch(`https://api.sequence.app/rpc/Indexer/GetTransactionHistory`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Access-Key': import.meta.env.VITE_PROJECT_ACCESS_KEY || ''
        },
        body: JSON.stringify({
          filter: {
            accountAddress: address
          },
          page: {
            page: 0,
            pageSize: 10
          },
          chainID: chainId.toString(),
          includeMetadata: true
        })
      })

      if (response.ok) {
        const data = await response.json()
        
        if (data.transactions && Array.isArray(data.transactions)) {
          const txList: Transaction[] = data.transactions.map((tx: any) => {
            const timestamp = tx.timestamp ? new Date(parseInt(tx.timestamp) * 1000) : new Date()
            const isReceive = tx.transfers?.[0]?.to?.toLowerCase() === address.toLowerCase()
            
            return {
              hash: tx.txnHash,
              type: isReceive ? 'Receive' : 'Send',
              amount: tx.transfers?.[0] ? `${formatUnits(tx.transfers[0].amounts?.[0] || '0', tx.transfers[0].decimals || 18)} ${tx.transfers[0].symbol || ''}` : 'N/A',
              date: timestamp.toLocaleString('de-DE'),
              status: 'Confirmed'
            }
          })
          
          setTransactions(txList)
        }
      }
    } catch (err) {
      console.error('Error fetching transactions:', err)
    } finally {
      setLoadingTx(false)
    }
  }

  if (!isConnected) {
    return (
      <div style={styles.container}>
        <p>Please connect your wallet to view dashboard</p>
      </div>
    )
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h2 style={styles.title}>CcOp User Dashboard</h2>
        <div style={styles.networkSelector}>
          <span style={styles.networkLabel}>Netzwerk:</span>
          <select
            value={chainId}
            onChange={(e) => {
              const newChainId = parseInt(e.target.value)
              switchChain?.({ chainId: newChainId })
              // Reload tokens and transactions after network switch
              setTimeout(() => {
                setTokens([])
                setTransactions([])
              }, 100)
            }}
            style={styles.networkDropdown}
          >
            <option value="137">Polygon</option>
            <option value="1">Ethereum</option>
            <option value="56">BSC</option>
            <option value="42161">Arbitrum</option>
            <option value="10">Optimism</option>
            <option value="8453">Base</option>
            <option value="41">Telos Testnet</option>
          </select>
          <span style={styles.currentNetwork}>{networkNames[chainId] || `Chain ${chainId}`}</span>
        </div>
      </div>

      {/* Tab Navigation */}
      <div style={styles.tabContainer}>
        <button
          style={{
            ...styles.tab,
            ...(activeTab === 'overview' ? styles.activeTab : {})
          }}
          onClick={() => setActiveTab('overview')}
        >
          Overview
        </button>
        <button
          style={{
            ...styles.tab,
            ...(activeTab === 'portfolio' ? styles.activeTab : {})
          }}
          onClick={() => setActiveTab('portfolio')}
        >
          Portfolio
        </button>
        <button
          style={{
            ...styles.tab,
            ...(activeTab === 'transactions' ? styles.activeTab : {})
          }}
          onClick={() => setActiveTab('transactions')}
        >
          Transactions
        </button>
      </div>

      {/* Tab Content */}
      {loading && (
        <div style={styles.loadingContainer}>
          <p>⏳ Lade Token-Daten...</p>
        </div>
      )}

      {error && (
        <div style={styles.errorContainer}>
          <p>⚠️ {error}</p>
        </div>
      )}

      {activeTab === 'overview' && (
        <div style={styles.content}>
          <div style={styles.card}>
            <h3 style={styles.cardTitle}>Wallet Address</h3>
            <p style={styles.address}>{address}</p>
          </div>

          <div style={styles.card}>
            <h3 style={styles.cardTitle}>Total Balance</h3>
            <p style={styles.balance}>
              {balance?.formatted} {balance?.symbol}
            </p>
            <p style={styles.fiat}>≈ $0.00 USD</p>
          </div>

          <div style={styles.statsGrid}>
            <div style={styles.statCard}>
              <h4 style={styles.statLabel}>Total Tokens</h4>
              <p style={styles.statValue}>{tokens.length}</p>
            </div>
            <div style={styles.statCard}>
              <h4 style={styles.statLabel}>Transactions</h4>
              <p style={styles.statValue}>{transactions.length}</p>
            </div>
            <div style={styles.statCard}>
              <h4 style={styles.statLabel}>Portfolio Value</h4>
              <p style={styles.statValue}>
                {tokens.length > 0 ? `${tokens.length} Token${tokens.length > 1 ? 's' : ''}` : 'N/A'}
              </p>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'portfolio' && (
        <div style={styles.content}>
          <h3 style={styles.sectionTitle}>Your Tokens ({tokens.length})</h3>
          {loading && (
            <div style={styles.infoCard}>
              <p style={styles.infoText}>⏳ Lade Token-Daten für {networkNames[chainId] || 'Netzwerk'}...</p>
              <p style={{...styles.infoText, fontSize: '12px', marginTop: '8px'}}>
                Dies kann einen Moment dauern, da echte Daten von {chainId === 137 ? 'Polygonscan' : chainId === 1 ? 'Etherscan' : 'Blockchain Explorer'} abgerufen werden.
              </p>
            </div>
          )}
          {tokens.length === 0 && !loading && (
            <div style={styles.infoCard}>
              <p style={styles.infoText}>❌ Keine Tokens gefunden auf {networkNames[chainId] || 'diesem Netzwerk'}</p>
              <p style={styles.infoText}>Versuche ein anderes Netzwerk zu wählen oder stelle sicher, dass Tokens im Wallet vorhanden sind.</p>
            </div>
          )}
          {!loading && tokens.map((token, index) => (
            <div key={index} style={styles.tokenCard}>
              <div style={styles.tokenInfo}>
                <div>
                  <h4 style={styles.tokenName}>{token.name}</h4>
                  <p style={styles.tokenSymbol}>{token.symbol}</p>
                </div>
                <div style={styles.tokenBalance}>
                  <p style={styles.balanceAmount}>{token.balance}</p>
                  <p style={styles.balanceValue}>{token.value}</p>
                </div>
              </div>
              <div style={styles.contractAddress}>
                <span style={styles.addressLabel}>Contract:</span>
                <span style={styles.addressText}>{token.address}</span>
                <button
                  style={styles.copyButton}
                  onClick={() => copyToClipboard(token.address)}
                  title="Copy address"
                >
                  📋
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {activeTab === 'transactions' && (
        <div style={styles.content}>
          <h3 style={styles.sectionTitle}>Recent Transactions</h3>
          {loadingTx && (
            <div style={styles.infoCard}>
              <p>⏳ Lade Transaktionen...</p>
            </div>
          )}
          {!loadingTx && transactions.length === 0 ? (
            <div style={styles.infoCard}>
              <h4 style={styles.infoTitle}>📊 Keine Transaktionen gefunden</h4>
              <p style={styles.infoText}>
                Es wurden keine Transaktionen für dieses Wallet auf {networkNames[chainId] || 'diesem Netzwerk'} gefunden.
              </p>
            </div>
          ) : (
            transactions.map((tx, index) => (
              <div key={index} style={styles.txCard}>
                <div style={styles.txHeader}>
                  <span style={{
                    ...styles.txType,
                    color: tx.type === 'Receive' ? '#10b981' : tx.type === 'Send' ? '#ef4444' : '#3b82f6'
                  }}>
                    {tx.type}
                  </span>
                  <span style={{
                    ...styles.txStatus,
                    backgroundColor: tx.status === 'Confirmed' ? '#10b98120' : '#f59e0b20',
                    color: tx.status === 'Confirmed' ? '#10b981' : '#f59e0b'
                  }}>
                    {tx.status}
                  </span>
                </div>
                <div style={styles.txAmount}>{tx.amount}</div>
                <div style={styles.txDate}>{tx.date}</div>
                <div style={styles.txHashContainer}>
                  <span style={styles.txHashLabel}>Hash:</span>
                  <span style={styles.txHash}>{formatHash(tx.hash)}</span>
                  <button
                    style={styles.hashButton}
                    onClick={() => copyToClipboard(tx.hash)}
                    title="Copy full hash"
                  >
                    📋
                  </button>
                  <button
                    style={styles.hashButton}
                    onClick={() => window.open(`https://teloscan.io/tx/${tx.hash}`, '_blank')}
                    title="View on Explorer"
                  >
                    🔗
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: '1200px',
    margin: '0 auto',
    padding: '20px',
    fontFamily: 'system-ui, -apple-system, sans-serif'
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '30px',
    flexWrap: 'wrap',
    gap: '20px'
  },
  title: {
    fontSize: '32px',
    fontWeight: 'bold',
    margin: 0,
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    backgroundClip: 'text'
  },
  networkSelector: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    background: 'linear-gradient(135deg, #ffffff 0%, #f9fafb 100%)',
    padding: '16px 24px',
    borderRadius: '12px',
    boxShadow: '0 4px 6px rgba(0,0,0,0.1)',
    border: '2px solid #e5e7eb'
  },
  networkLabel: {
    fontSize: '14px',
    fontWeight: '600',
    color: '#6b7280'
  },
  networkDropdown: {
    padding: '12px 16px',
    borderRadius: '8px',
    border: '2px solid #667eea',
    background: 'white',
    fontSize: '16px',
    fontWeight: '600',
    cursor: 'pointer',
    outline: 'none',
    color: '#111827',
    minWidth: '160px',
    boxShadow: '0 2px 4px rgba(102, 126, 234, 0.2)',
    transition: 'all 0.2s ease'
  },
  currentNetwork: {
    fontSize: '14px',
    fontWeight: '600',
    color: '#667eea',
    background: '#667eea20',
    padding: '4px 12px',
    borderRadius: '6px'
  },
  tabContainer: {
    display: 'flex',
    gap: '10px',
    marginBottom: '30px',
    borderBottom: '2px solid #e5e7eb'
  },
  tab: {
    padding: '12px 24px',
    border: 'none',
    background: 'transparent',
    cursor: 'pointer',
    fontSize: '16px',
    fontWeight: '500',
    color: '#6b7280',
    borderBottom: '2px solid transparent',
    marginBottom: '-2px',
    transition: 'all 0.3s ease'
  },
  activeTab: {
    color: '#667eea',
    borderBottom: '2px solid #667eea'
  },
  content: {
    animation: 'fadeIn 0.3s ease'
  },
  card: {
    background: 'white',
    borderRadius: '12px',
    padding: '24px',
    marginBottom: '20px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    border: '1px solid #e5e7eb'
  },
  cardTitle: {
    fontSize: '14px',
    fontWeight: '600',
    color: '#6b7280',
    marginBottom: '12px',
    textTransform: 'uppercase',
    letterSpacing: '0.5px'
  },
  address: {
    fontSize: '18px',
    fontFamily: 'monospace',
    color: '#111827',
    wordBreak: 'break-all'
  },
  balance: {
    fontSize: '36px',
    fontWeight: 'bold',
    color: '#111827',
    marginBottom: '8px'
  },
  fiat: {
    fontSize: '18px',
    color: '#6b7280'
  },
  statsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
    gap: '16px'
  },
  statCard: {
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    borderRadius: '12px',
    padding: '20px',
    color: 'white'
  },
  statLabel: {
    fontSize: '14px',
    fontWeight: '500',
    opacity: 0.9,
    marginBottom: '8px'
  },
  statValue: {
    fontSize: '28px',
    fontWeight: 'bold'
  },
  sectionTitle: {
    fontSize: '24px',
    fontWeight: 'bold',
    marginBottom: '20px',
    color: '#111827'
  },
  tokenCard: {
    background: 'white',
    borderRadius: '12px',
    padding: '20px',
    marginBottom: '16px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    border: '1px solid #e5e7eb',
    transition: 'transform 0.2s ease, box-shadow 0.2s ease'
  },
  tokenInfo: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '12px'
  },
  tokenName: {
    fontSize: '18px',
    fontWeight: '600',
    color: '#111827',
    marginBottom: '4px'
  },
  tokenSymbol: {
    fontSize: '14px',
    color: '#6b7280'
  },
  tokenBalance: {
    textAlign: 'right'
  },
  balanceAmount: {
    fontSize: '20px',
    fontWeight: 'bold',
    color: '#111827',
    marginBottom: '4px'
  },
  balanceValue: {
    fontSize: '14px',
    color: '#10b981'
  },
  contractAddress: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '12px',
    background: '#f9fafb',
    borderRadius: '8px',
    fontSize: '13px',
    fontFamily: 'monospace'
  },
  addressLabel: {
    color: '#6b7280',
    fontWeight: '600'
  },
  addressText: {
    color: '#111827',
    flex: 1,
    overflow: 'hidden',
    textOverflow: 'ellipsis'
  },
  copyButton: {
    background: '#667eea',
    border: 'none',
    borderRadius: '6px',
    padding: '6px 10px',
    cursor: 'pointer',
    fontSize: '14px',
    transition: 'transform 0.2s ease'
  },
  txCard: {
    background: 'white',
    borderRadius: '12px',
    padding: '20px',
    marginBottom: '16px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    border: '1px solid #e5e7eb'
  },
  txHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '12px'
  },
  txType: {
    fontSize: '16px',
    fontWeight: '600'
  },
  txStatus: {
    padding: '4px 12px',
    borderRadius: '12px',
    fontSize: '12px',
    fontWeight: '600'
  },
  txAmount: {
    fontSize: '20px',
    fontWeight: 'bold',
    color: '#111827',
    marginBottom: '8px'
  },
  txDate: {
    fontSize: '14px',
    color: '#6b7280',
    marginBottom: '12px'
  },
  txHashContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '12px',
    background: '#f9fafb',
    borderRadius: '8px',
    fontSize: '13px',
    fontFamily: 'monospace'
  },
  txHashLabel: {
    color: '#6b7280',
    fontWeight: '600'
  },
  txHash: {
    color: '#111827',
    flex: 1
  },
  hashButton: {
    background: '#667eea',
    border: 'none',
    borderRadius: '6px',
    padding: '6px 10px',
    cursor: 'pointer',
    fontSize: '14px',
    transition: 'transform 0.2s ease'
  },
  infoCard: {
    background: 'white',
    borderRadius: '12px',
    padding: '24px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    border: '1px solid #e5e7eb'
  },
  infoTitle: {
    fontSize: '18px',
    fontWeight: '600',
    color: '#111827',
    marginBottom: '16px'
  },
  infoText: {
    fontSize: '14px',
    color: '#6b7280',
    lineHeight: '1.6',
    marginBottom: '12px'
  },
  infoList: {
    fontSize: '14px',
    color: '#6b7280',
    lineHeight: '1.8',
    marginBottom: '16px',
    paddingLeft: '20px'
  },
  link: {
    color: '#667eea',
    textDecoration: 'none',
    fontWeight: '600'
  },
  loadingContainer: {
    background: 'white',
    borderRadius: '12px',
    padding: '24px',
    textAlign: 'center',
    marginBottom: '20px',
    border: '1px solid #e5e7eb'
  },
  errorContainer: {
    background: '#fef2f2',
    borderRadius: '12px',
    padding: '24px',
    textAlign: 'center',
    marginBottom: '20px',
    border: '1px solid #fecaca',
    color: '#dc2626'
  }
}
