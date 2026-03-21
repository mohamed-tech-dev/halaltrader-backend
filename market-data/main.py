from fastapi import FastAPI, HTTPException
import yfinance as yf
import pandas as pd
from typing import Optional

app = FastAPI(title="HalalTrader Market Data Service")


def compute_rsi(series: pd.Series, period: int = 14) -> float:
    delta = series.diff()
    gain = delta.where(delta > 0, 0.0).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0.0)).rolling(window=period).mean()
    rs = gain / loss
    rsi = 100 - (100 / (1 + rs))
    return round(float(rsi.iloc[-1]), 2)


def compute_macd(series: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> dict:
    ema_fast = series.ewm(span=fast, adjust=False).mean()
    ema_slow = series.ewm(span=slow, adjust=False).mean()
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal, adjust=False).mean()
    return {
        "macd": round(float(macd_line.iloc[-1]), 4),
        "signal": round(float(signal_line.iloc[-1]), 4),
        "histogram": round(float((macd_line - signal_line).iloc[-1]), 4),
    }


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/price/{symbol}")
def get_price(symbol: str):
    try:
        ticker = yf.Ticker(symbol)
        hist = ticker.history(period="3mo")
        if hist.empty:
            raise HTTPException(status_code=404, detail=f"No data for symbol: {symbol}")

        close = hist["Close"]
        current_price = round(float(close.iloc[-1]), 4)
        prev_price = round(float(close.iloc[-2]), 4) if len(close) > 1 else current_price
        change_pct = round(((current_price - prev_price) / prev_price) * 100, 2) if prev_price else 0.0
        volume = int(hist["Volume"].iloc[-1]) if "Volume" in hist.columns else 0

        rsi = compute_rsi(close)
        macd_data = compute_macd(close)
        ma20 = round(float(close.rolling(window=20).mean().iloc[-1]), 4)
        ma50 = round(float(close.rolling(window=50).mean().iloc[-1]), 4)

        return {
            "symbol": symbol.upper(),
            "price": current_price,
            "change_pct": change_pct,
            "volume": volume,
            "rsi": rsi,
            "macd": macd_data["macd"],
            "macd_signal": macd_data["signal"],
            "macd_histogram": macd_data["histogram"],
            "ma20": ma20,
            "ma50": ma50,
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/history/{symbol}")
def get_history(symbol: str, period: Optional[str] = "1mo"):
    try:
        ticker = yf.Ticker(symbol)
        hist = ticker.history(period=period)
        if hist.empty:
            raise HTTPException(status_code=404, detail=f"No data for symbol: {symbol}")

        candles = []
        for date, row in hist.iterrows():
            candles.append({
                "date": date.strftime("%Y-%m-%d"),
                "open": round(float(row["Open"]), 4),
                "high": round(float(row["High"]), 4),
                "low": round(float(row["Low"]), 4),
                "close": round(float(row["Close"]), 4),
                "volume": int(row["Volume"]),
            })
        return candles
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/news/{symbol}")
def get_news(symbol: str):
    try:
        ticker = yf.Ticker(symbol)
        news = ticker.news or []
        results = []
        for item in news[:3]:
            title = item.get("content", {}).get("title") or item.get("title", "")
            if title:
                results.append({"title": title})
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
