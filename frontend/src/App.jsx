import { useState, useRef } from 'react'

const API_URL = '/api/convert'

export default function App() {
  const [preview, setPreview] = useState(null)
  const [file, setFile] = useState(null)
  const [isPdf, setIsPdf] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)
  const fileInputRef = useRef(null)

  function handleFileChange(e) {
    const selected = e.target.files?.[0]
    if (!selected) return

    setFile(selected)
    setResult(null)
    setError(null)
    setIsPdf(selected.type === 'application/pdf')
    setPreview(selected.type === 'application/pdf' ? null : URL.createObjectURL(selected))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!file) {
      setError('Prvo odaberi sliku ili PDF s barkodom.')
      return
    }

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const formData = new FormData()
      formData.append('file', file)

      const response = await fetch(API_URL, {
        method: 'POST',
        body: formData,
      })

      const data = await response.json()

      if (!response.ok) {
        throw new Error(data.error || 'Konverzija nije uspjela.')
      }

      setResult(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function handleReset() {
    setFile(null)
    setPreview(null)
    setIsPdf(false)
    setResult(null)
    setError(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  function downloadQr() {
    if (!result?.qrCodeBase64) return
    const link = document.createElement('a')
    link.href = result.qrCodeBase64
    link.download = 'qr-kod.png'
    link.click()
  }

  return (
    <div className="page">
      <div className="card">
        <h1>Barkod → QR kod</h1>
        <p className="subtitle">
          Učitaj sliku 1D/2D barkoda (Code128, EAN, DataMatrix, PDF417...)
          ili cijeli PDF (npr. bankovni izvod s uplatnicom) i dobit ćeš QR
          kod s istim sadržajem.
        </p>

        <form onSubmit={handleSubmit} className="form">
          <label className="file-drop">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*,application/pdf"
              onChange={handleFileChange}
              hidden
            />
            {isPdf ? (
              <span className="pdf-chip">📄 {file.name}</span>
            ) : preview ? (
              <img src={preview} alt="Pregled barkoda" className="preview-img" />
            ) : (
              <span>Klikni ili povuci sliku ili PDF ovdje</span>
            )}
          </label>

          <div className="actions">
            <button type="submit" disabled={loading || !file}>
              {loading ? 'Obrađujem...' : 'Konvertiraj u QR kod'}
            </button>
            <button type="button" className="secondary" onClick={handleReset}>
              Reset
            </button>
          </div>
        </form>

        {error && <div className="error">{error}</div>}

        {result && (
          <div className="result">
            <div className="result-row">
              <span className="label">Prepoznati format:</span>
              <span>
                {result.sourceFormat}
                {result.detectedType === 'HUB3' && ' (HUB-3 uplatnica → EPC QR)'}
                {result.sourcePage && ` — pronađeno na stranici ${result.sourcePage} PDF-a`}
              </span>
            </div>

            {result.warning && <div className="warning">{result.warning}</div>}

            {result.detectedType === 'HUB3' ? (
              <>
                <div className="result-row">
                  <span className="label">Primatelj:</span>
                  <span>{result.recipientName || '—'}</span>
                </div>
                <div className="result-row">
                  <span className="label">IBAN:</span>
                  <code>{result.iban || '—'}</code>
                </div>
                {result.bic && (
                  <div className="result-row">
                    <span className="label">BIC:</span>
                    <code>{result.bic}</code>
                  </div>
                )}
                <div className="result-row">
                  <span className="label">Iznos:</span>
                  <span>
                    {result.amount > 0
                      ? `${result.amount.toFixed(2)} ${result.currency}`
                      : 'Otvoreni iznos (upisuješ sam u banking app-u)'}
                  </span>
                </div>
                {result.reference && (
                  <div className="result-row">
                    <span className="label">Poziv na broj:</span>
                    <span>{result.reference}</span>
                  </div>
                )}
                {result.description && (
                  <div className="result-row">
                    <span className="label">Opis plaćanja:</span>
                    <span>{result.description}</span>
                  </div>
                )}
              </>
            ) : (
              <div className="result-row">
                <span className="label">Dekodirani sadržaj:</span>
                <code>{result.decodedText}</code>
              </div>
            )}

            <div className="qr-wrapper">
              <img src={result.qrCodeBase64} alt="Generirani QR kod" />
            </div>

            <button type="button" onClick={downloadQr}>
              Preuzmi QR kod
            </button>

            <details className="debug">
              <summary>Prikaži točan sadržaj QR koda (debug)</summary>
              <pre>{result.qrContent}</pre>
            </details>
          </div>
        )}
      </div>
    </div>
  )
}
