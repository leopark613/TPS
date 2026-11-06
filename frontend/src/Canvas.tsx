import { useRef } from 'react';
import type { KeyboardEvent, PointerEvent } from 'react';
import type { Document, Element } from './types';
import { elements, moveFrame, replaceElement, safeColor, safeFontSize } from './document';

interface Props { document: Document; selected: string | null; onSelect: (id: string | null) => void; onChange: (document: Document) => void; readOnly: boolean }
export function Canvas({ document: doc, selected, onSelect, onChange, readOnly }: Props) {
  const svg = useRef<SVGSVGElement>(null);
  const drag = useRef<{ id: string; startX: number; startY: number; x: number; y: number; scale: number; pointerId: number } | null>(null);
  const section = doc.sections[0];
  if (!section) return <div className="empty">이 문서에는 표시할 페이지가 없습니다.</div>;
  const page = section.page;
  const width = page.width;
  const height = page.height;
  const begin = (event: PointerEvent<SVGGElement>, element: Element) => {
    event.stopPropagation(); onSelect(element.id);
    if (readOnly || event.button !== 0 || !svg.current) return;
    event.preventDefault();
    drag.current = { id: element.id, startX: event.clientX, startY: event.clientY, x: element.frame.x, y: element.frame.y, scale: width / svg.current.getBoundingClientRect().width, pointerId: event.pointerId };
    svg.current.setPointerCapture(event.pointerId);
  };
  const move = (event: PointerEvent<SVGSVGElement>) => {
    const d = drag.current;
    if (!d || readOnly) return;
    const x = Math.round(d.x + (event.clientX - d.startX) * d.scale);
    const y = Math.round(d.y + (event.clientY - d.startY) * d.scale);
    onChange(replaceElement(doc, d.id, el => ({ ...el, frame: { ...el.frame, x, y } })));
  };
  const end = () => { drag.current = null; };
  const keyMove = (event: KeyboardEvent<SVGGElement>, element: Element) => {
    if (readOnly) return;
    const step = event.shiftKey ? 10000 : 1000;
    const delta: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
    if (delta[event.key]) { event.preventDefault(); onChange(replaceElement(doc, element.id, e => ({ ...e, frame: moveFrame(e.frame, ...delta[event.key]) }))); }
  };
  return <div className="canvas-stage">
    <div className="paper-meta"><span>{Math.round(width / 1000)} × {Math.round(height / 1000)} mm</span><span>화면 배치 보기</span></div>
    <svg ref={svg} className="paper" role="group" aria-label="양식 캔버스" viewBox={`0 0 ${width} ${height}`} onPointerMove={move} onPointerUp={end} onPointerCancel={end} onLostPointerCapture={end} onPointerDown={() => onSelect(null)}>
      <rect width={width} height={height} fill="white" />
      <rect className="page-margin" x={page.margins.left} y={page.margins.top} width={Math.max(0, width - page.margins.left - page.margins.right)} height={Math.max(0, height - page.margins.top - page.margins.bottom)} />
      <g transform={`translate(${page.margins.left},${page.margins.top})`}>
        {elements(doc).filter(e => ['tps.text', 'tps.rectangle'].includes(e.type)).map(element => {
          const f = element.frame; const active = selected === element.id; const font = safeFontSize(element.properties.fontSizePt) * 352.778;
          const text = typeof element.properties.text === 'string' ? element.properties.text : '';
          return <g key={element.id} className={`canvas-element ${active ? 'selected' : ''}`} tabIndex={0} role="button" aria-label={element.type === 'tps.text' ? `텍스트 요소: ${text.slice(0, 40)}` : `사각형 요소: ${element.id}`} aria-pressed={active}
            transform={`translate(${f.x},${f.y}) rotate(${(f.rotationMilliDegrees ?? 0) / 1000},${f.width / 2},${f.height / 2})`} onPointerDown={e => begin(e, element)} onFocus={() => onSelect(element.id)} onKeyDown={e => keyMove(e, element)}>
            {element.type === 'tps.rectangle' ? <rect width={f.width} height={f.height} fill={safeColor(element.properties.fill, '#eef2f6')} stroke={safeColor(element.properties.stroke, '#2463a6')} strokeWidth={300} /> :
              <svg width={f.width} height={f.height} overflow="hidden"><text x={0} y={font} fill={safeColor(element.properties.color, '#17324d')} fontSize={font}>{text.split('\n').map((line, i) => <tspan key={i} x={0} dy={i === 0 ? 0 : font * 1.3}>{line || ' '}</tspan>)}</text></svg>}
            <rect className="element-hitbox" width={f.width} height={f.height} />
            {active && <><rect className="element-outline" width={f.width} height={f.height} /><rect className="element-handle" width={1300} height={1300} x={-650} y={-650} /><rect className="element-handle" width={1300} height={1300} x={f.width - 650} y={f.height - 650} /></>}
          </g>;
        })}
      </g>
    </svg>
    <p className="canvas-help">요소를 끌어 이동하거나 방향키로 1 mm씩 옮기세요. <span>실제 인쇄 미리보기는 후속 단계에서 제공합니다.</span></p>
  </div>;
}
