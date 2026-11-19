#!/usr/bin/env python3
"""Regenerate the fixed physics corpus with mpmath 1.3.0 at 80 decimal digits.

Only regeneration needs mpmath; the calculator tests read the checked-in answers.
Run from any directory: python3 scripts/generate-physics-stress-cases.py
"""
import ast
import json
from pathlib import Path
import mpmath as mp

mp.mp.dps = 80
ROOT = Path(__file__).resolve().parents[1]
CASES = []


def case(name, quantity, unit, left, right, expression, branch="Unique real solution"):
    CASES.append(dict(name=name, quantity=quantity, unit=unit, left=left, right=right,
                      expression=expression, branch=branch))


case("Projectile flight time", "time", "s",
     "3.725", "128.4575+37.8525*sin(0.73125)*x-(9.80665*x^2)/2",
     "(37.8525*sin(0.73125)+sqrt((37.8525*sin(0.73125))^2+2*9.80665*(128.4575-3.725)))/9.80665",
     "Positive time; discard the negative quadratic root")

case("Incline, friction and pulley inertia", "acceleration", "m/s^2",
     "(12.875+3.425+0.01675/0.0425^2)*x",
     "12.875*9.80665*(sin(0.638)-0.1875*cos(0.638))-3.425*9.80665-0.2875/0.0425",
     "(12.875*9.80665*(sin(0.638)-0.1875*cos(0.638))-3.425*9.80665-0.2875/0.0425)/(12.875+3.425+0.01675/0.0425^2)")

capacitance = "((2.875e-6*6.425e-6)/(2.875e-6+6.425e-6)+0.375e-6)"
case("Damped RLC frequency with combined capacitors", "frequency", "Hz",
     "(2*pi*x)^2", f"1/(47.35e-3*{capacitance})-((18.725+4.375)/(2*47.35e-3))^2",
     f"sqrt(1/(47.35e-3*{capacitance})-((18.725+4.375)/(2*47.35e-3))^2)/(2*pi)",
     "Positive oscillation frequency")

gravity = "(6.67430e-11*5.97219e24)"
radius = "(6.371e6+421.375e3)"
axis = "(((6.371e6+421.375e3)+(6.371e6+35786.425e3))/2)"
case("Elliptical orbit speed at perigee", "speed", "m/s",
     f"x^2/2-{gravity}/{radius}", f"-{gravity}/(2*{axis})",
     f"sqrt({gravity}*(2/{radius}-1/{axis}))", "Positive speed")

momentum = "sqrt((7.3525e-23+3.1875e-23)^2+(2.675e-23-9.125e-24)^2+(1.2375e-23)^2)"
case("Relativistic speed from three momentum components", "speed", "m/s",
     "9.1093837015e-31*x/sqrt(1-(x/299792458)^2)", momentum,
     f"({momentum}/9.1093837015e-31)/sqrt(1+({momentum}/(9.1093837015e-31*299792458))^2)",
     "0 < x < 299792458")

voltage = "(12.175*8.215e3/(4.725e3+8.215e3))"
time_constant = "(((4.725e3*8.215e3)/(4.725e3+8.215e3))*(2.275e-6+0.825e-6))"
case("Loaded RC charging threshold", "time", "s", "1.2375",
     f"{voltage}+(0.1875-{voltage})*exp(-x/{time_constant})",
     f"-{time_constant}*ln((1.2375-{voltage})/(0.1875-{voltage}))")

resistance = "(1/(37.875*1.2375)+0.01275/(16.425*1.2375)+0.0875/(0.04275*1.2375)+0.00625/(0.1875*1.2375)+1/(8.725*1.2375))"
case("Three-layer wall with convection on both sides", "heat flow", "W",
     "563.15-295.65", f"x*{resistance}", f"(563.15-295.65)/{resistance}")

absorbed = "(1361.275*(1-0.2875)*pi*(6.371e6)^2+4.725e13)"
radiative = "(0.9275*5.670374419e-8*4*pi*(6.371e6)^2)"
case("Radiative equilibrium including background temperature", "temperature", "K",
     f"{radiative}*(x^4-2.72548^4)", absorbed,
     f"({absorbed}/{radiative}+2.72548^4)^(1/4)", "Positive absolute temperature")

head = "((287500-101325)/998.275+9.80665*(14.875-2.375))"
losses = "(1-(0.02875/0.0875)^4+0.03125*(18.725/0.02875)+0.475+1.875+0.325)"
case("Bernoulli flow with pipe friction and minor losses", "outlet speed", "m/s",
     head, f"x^2*{losses}/2", f"sqrt(2*{head}/{losses})", "Positive flow speed")

stiffness = "(0.725*(2*pi*8.125)^2-0.725*(2*pi*7.375)^2)"
damping = "((0.1875+0.0625)*(2*pi*7.375))"
drive = f"((2.875+0.425)*cos(2*pi*7.375*0.2375-atan2({damping},{stiffness})))"
amplitude = f"sqrt({stiffness}^2+{damping}^2)"
case("Driven damped oscillator at a specified instant", "displacement", "m",
     drive, f"x*{amplitude}", f"{drive}/{amplitude}")


def parse(source):
    source = source.replace("^", "**")
    return source, ast.parse(source, mode="eval").body


def evaluate(source, x=mp.mpf(0)):
    source, root = parse(source)

    def visit(node):
        if isinstance(node, ast.Constant):
            return mp.mpf(ast.get_source_segment(source, node))
        if isinstance(node, ast.Name):
            return {"x": x, "pi": mp.pi}[node.id]
        if isinstance(node, ast.UnaryOp):
            return -visit(node.operand) if isinstance(node.op, ast.USub) else visit(node.operand)
        if isinstance(node, ast.BinOp):
            a, b = visit(node.left), visit(node.right)
            return {ast.Add: lambda: a+b, ast.Sub: lambda: a-b, ast.Mult: lambda: a*b,
                    ast.Div: lambda: a/b, ast.Pow: lambda: a**b}[type(node.op)]()
        if isinstance(node, ast.Call):
            return {"sqrt": mp.sqrt, "sin": mp.sin, "cos": mp.cos, "ln": mp.log,
                    "exp": mp.exp, "atan2": mp.atan2}[node.func.id](*(visit(a) for a in node.args))
        raise ValueError(ast.dump(node))

    return visit(root)


def latex(source):
    source, root = parse(source)

    def visit(node):
        if isinstance(node, ast.Constant):
            return ast.get_source_segment(source, node)
        if isinstance(node, ast.Name):
            return r"\pi" if node.id == "pi" else node.id
        if isinstance(node, ast.UnaryOp):
            return ("-" if isinstance(node.op, ast.USub) else "+") + "(" + visit(node.operand) + ")"
        if isinstance(node, ast.BinOp):
            a, b = visit(node.left), visit(node.right)
            if isinstance(node.op, ast.Div):
                return r"\frac{" + a + "}{" + b + "}"
            if isinstance(node.op, ast.Pow):
                return "(" + a + ")^{" + b + "}"
            op = {ast.Add: "+", ast.Sub: "-", ast.Mult: r"\cdot "}[type(node.op)]
            return "(" + a + op + b + ")"
        if isinstance(node, ast.Call):
            args = [visit(a) for a in node.args]
            if node.func.id == "sqrt":
                return r"\sqrt{" + args[0] + "}"
            return node.func.id + "(" + ",".join(args) + ")"
        raise ValueError(ast.dump(node))

    return visit(root)


lines = ["TEN PHYSICS STRESS CASES", "", "Chosen textbook-style scenarios, with SI units and radians.",
         "Each case solves for x. Copy 'Rearranged' into the calculator.",
         "Import docs/physics-stress-worksheet.json in Demos to load all ten.",
         "Answers independently computed with mpmath 1.3.0 at 80 decimal digits.",
         "The original equations are checked at those answers before writing fixtures.", ""]
rows = []
for number, item in enumerate(CASES, 1):
    result = evaluate(item["expression"])
    left, right = evaluate(item["left"], result), evaluate(item["right"], result)
    scale = max(abs(left), abs(right)) or mp.mpf(1)
    assert abs(left-right)/scale < mp.mpf("1e-65"), item["name"]
    item.update(id=number, expected=mp.nstr(result, 60), residualScale=mp.nstr(scale, 25),
                latex="x="+latex(item["expression"]))
    lines += [f"{number}. {item['name']} — x is {item['quantity']} ({item['unit']})",
              f"Original:   {item['left']}={item['right']}",
              f"Rearranged: x={item['expression']}",
              f"Expected:   x ≈ {mp.nstr(result, 16)} {item['unit']}",
              f"Branch:     {item['branch']}", ""]
    label = f"{number}. {item['name']}\nExpected x = {mp.nstr(result, 16)} {item['unit']}\n{item['branch']}"
    rows += [dict(id=2*number-1, text="note("+json.dumps(label)+")", color=-3849152),
             dict(id=2*number, text="x="+item["expression"], color=-3849152, hidden=True)]

fixture = ROOT/"app/src/sharedTest/resources/physics-stress-cases.json"
fixture.parent.mkdir(parents=True, exist_ok=True)
fixture.write_text(json.dumps(CASES, indent=2)+"\n", encoding="utf-8")
(ROOT/"docs/PHYSICS_STRESS_CASES.txt").write_text("\n".join(lines), encoding="utf-8")
worksheet = dict(formatVersion=1, id="67d4c68f-17f5-4f5f-b42b-185a3b629074",
                 title="10 ugly physics equations", expressions=rows, activeId=2,
                 degreeMode=False, precisionDigits=15, updatedAt=0)
(ROOT/"docs/physics-stress-worksheet.json").write_text(json.dumps(worksheet, indent=2)+"\n", encoding="utf-8")
print(f"Generated {len(CASES)} cases; all 80-digit substitution checks passed.")
for item in CASES:
    print(f"{item['id']:2}. {item['name']}: {mp.nstr(mp.mpf(item['expected']), 14)} {item['unit']}; {len(item['expression'])} source / {len(item['latex'])} LaTeX characters")
