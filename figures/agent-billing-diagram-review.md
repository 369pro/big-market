# Diagram review

Both diagrams accepted: 9/10 after widening label wrapping.

- File correctness: Mermaid CLI renders PNG and SVG successfully; Markdown wrappers match raw sources exactly.
- Arrow correctness: all arrows follow the verified successful reservation, upstream call and settlement order.
- Content: amounts match the local ledger; initial balance of ¥10 is explicitly hypothetical.
- Completeness: wallet example shows budget, reservation, usage, refund and final balance; service diagram shows frontend, Java, Python, provider interaction and storage ownership.
- Visual review: inspected both final PNGs; Chinese labels are legible, amounts are complete, arrows do not cross, colors separate Java billing from Python execution.
- Scope: figures cover successful calls; failure, excess-charge and cancellation branches are explained in the accompanying walkthrough.
- Initial issue fixed: default label wrapping made diagrams too narrow and tall; expanded wrapping width to 520.

Re-render: `mmdc -i figures/agent-wallet-example.mmd -o figures/agent-wallet-example.png -b white -s 2`.
