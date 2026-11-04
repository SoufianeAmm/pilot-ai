# UML design: identity, organizations, audit and AI

[FlowPilot-UML-Identity-Organizations-AI.pdf](FlowPilot-UML-Identity-Organizations-AI.pdf) contains the class, sequence and use-case diagrams for the modules owned by Soufiane, plus the interfaces the other teams implement for access control and the AI analyst. The names in it are the contract between teams: change a name by pull request, updating the document first.

## Regenerating the PDF
The diagrams are defined in code in [source/build_pdf.py](source/build_pdf.py) and drawn by [source/uml.py](source/uml.py). Edit the Python, not the PDF.

```powershell
python -m venv .venv
.venv\Scripts\pip install reportlab
.venv\Scripts\python docs\uml\source\build_pdf.py
```
This needs the Windows fonts Arial and Consolas. Set `font_dir` in `uml.register_fonts()` on other systems.
