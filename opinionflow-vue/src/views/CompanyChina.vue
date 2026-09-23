/* ── 深色模式柱形图、图表弹窗等字体及背景色不适修复 ── */
html.dark .trendPanel,
html.dark .bsPiePanel,
html.dark .peerPieZoomPanel {
  background: rgba(30, 34, 40, 0.95);
  color: #e5eaf3;
  border-color: rgba(255, 255, 255, 0.15);
}
html.dark .trendPanel .readerHead,
html.dark .bsPiePanel .peerPieZoomHead,
html.dark .peerPieZoomPanel .peerPieZoomHead {
  background: rgba(36, 40, 46, 0.88);
  color: #e5eaf3;
  border-bottom-color: rgba(255, 255, 255, 0.1);
}
html.dark .trendPanel .readerTitle,
html.dark .bsPiePanel .peerPieZoomTitle,
html.dark .peerPieZoomPanel .peerPieZoomTitle {
  color: #f2f4f7;
}
html.dark .trendPanel .readerSub,
html.dark .bsPiePanel .peerPieZoomSub,
html.dark .peerPieZoomPanel .peerPieZoomSub {
  color: #b6bdc9;
}
html.dark .peerRank {
  background: rgba(36, 40, 46, 0.5);
  border-left-color: rgba(255, 255, 255, 0.1);
}
html.dark .peerRankTitle {
  color: #e5eaf3;
}
html.dark .peerRankRow {
  background: rgba(64, 158, 255, 0.15);
  color: #e5eaf3;
}
html.dark .peerRankName {
  color: #e5eaf3;
}
html.dark .peerRankEmpty {
  color: #9aa3ad;
}
html.dark .bsPieList {
  background: rgba(36, 40, 46, 0.5);
  border-left-color: rgba(255, 255, 255, 0.1);
}
html.dark .bsPieListTitle {
  color: #e5eaf3;
}
html.dark .bsPieRow {
  background: rgba(64, 158, 255, 0.15);
  color: #e5eaf3;
}
html.dark .bsPieRow:hover {
  background: rgba(64, 158, 255, 0.28);
}
html.dark .bsPieRowName {
  color: #e5eaf3;
}
html.dark .bsPieRowPct {
  color: #b6bdc9;
}
html.dark .bsPieNote {
  color: #f3c26b;
}
html.dark .bsPieEmptyTip {
  color: #9aa3ad;
}
/* ── 深色模式：柱形图/图表弹窗内数据标签文字改为纯白 #ffffff ──
   ECharts 若使用 SVG 渲染，其数据标签为 <text>/<tspan> 元素，
   需同时覆盖 color（HTML 文本）与 fill（SVG 文本）以保证纯白。 */
html.dark .trendPanel,
html.dark .bsPiePanel,
html.dark .peerPieZoomPanel {
  color: #ffffff;
}
html.dark .trendPanel text,
html.dark .trendPanel tspan,
html.dark .bsPiePanel text,
html.dark .bsPiePanel tspan,
html.dark .peerPieZoomPanel text,
html.dark .peerPieZoomPanel tspan,
html.dark .trendPanel [style*="color"],
html.dark .bsPiePanel [style*="color"],
html.dark .peerPieZoomPanel [style*="color"] {
  color: #ffffff !important;
  fill: #ffffff !important;
}

