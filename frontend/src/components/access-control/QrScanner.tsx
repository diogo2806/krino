import { Camera, CameraOff } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Button } from '../button/Button';
import { StateMessage } from '../state/StateMessage';

type BarcodeResult = { rawValue: string; };
type BarcodeDetectorInstance = { detect: (source: HTMLVideoElement) => Promise<BarcodeResult[]>; };
type BarcodeDetectorConstructor = new (options?: { formats?: string[]; }) => BarcodeDetectorInstance;
type Props = { onDetected: (value: string) => void; disabled?: boolean; };

export function QrScanner({ onDetected, disabled = false }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const intervalRef = useRef<number | null>(null);
  const busyRef = useRef(false);
  const [active, setActive] = useState(false);
  const [error, setError] = useState('');

  const stop = useCallback(() => {
    if (intervalRef.current !== null) window.clearInterval(intervalRef.current);
    intervalRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setActive(false);
  }, []);

  useEffect(() => stop, [stop]);

  async function start() {
    setError('');
    const Detector = (window as unknown as { BarcodeDetector?: BarcodeDetectorConstructor; }).BarcodeDetector;
    if (!Detector) {
      setError('A leitura automática de QR Code não está disponível neste navegador. Use o código manual ou outro navegador compatível.');
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('A câmera não está disponível neste dispositivo. Use o código manual.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) { stop(); return; }
      video.srcObject = stream;
      await video.play();
      setActive(true);
      const detector = new Detector({ formats: ['qr_code'] });
      intervalRef.current = window.setInterval(async () => {
        if (busyRef.current || !videoRef.current || videoRef.current.readyState < 2) return;
        busyRef.current = true;
        try {
          const results = await detector.detect(videoRef.current);
          const value = results[0]?.rawValue?.trim();
          if (value) {
            stop();
            onDetected(value);
          }
        } catch {
          setError('Não foi possível ler o QR Code. Reposicione a câmera ou use o código manual.');
        } finally {
          busyRef.current = false;
        }
      }, 500);
    } catch {
      setError('Não foi possível acessar a câmera. Verifique a permissão do navegador ou use o código manual.');
      stop();
    }
  }

  return <section className="qr-scanner" aria-label="Leitura de QR Code"><div className="qr-scanner__viewport"><video ref={videoRef} className={active ? 'qr-scanner__video' : 'qr-scanner__video qr-scanner__video--inactive'} playsInline muted /><div className="qr-scanner__guide" aria-hidden="true" /></div><div className="qr-scanner__actions">{active ? <Button type="button" variant="ghost" onClick={stop}><CameraOff aria-hidden="true" size={18} />Parar câmera</Button> : <Button type="button" onClick={() => void start()} disabled={disabled}><Camera aria-hidden="true" size={18} />Ler QR Code</Button>}</div>{error ? <StateMessage kind="error" title="Leitura indisponível" message={error} /> : null}</section>;
}
