# Adaptive billing figures — visual acceptance

Both diagrams: ACCEPT, 9/10.

- PNG and SVG rendered successfully with Mermaid CLI.
- Raw sources and Markdown wrappers are identical.
- Inspected the final PNGs: text, Chinese labels, amounts and arrows are visible with no clipping or crossings.
- All arrows follow authorization before model invocation and settlement after usage.
- Example has 20% input headroom, approved 950 output tokens, ¥0.010 hold, ¥0.0036 settlement, ¥0.0064 refund, ¥0.0084 final wallet.
- Sources are valid success-path summaries; the accompanying document explains insufficient funds, cancellation, late usage and unknown usage.
- Font size 20px, wide labels, SVG for scalable preview.

Re-render: `mmdc -i figures/agent-adaptive-limit-flow.mmd -o figures/agent-adaptive-limit-flow.svg -b white`.
