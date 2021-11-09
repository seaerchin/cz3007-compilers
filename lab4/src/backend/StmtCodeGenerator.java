package backend;

import java.util.HashMap;

import soot.Unit;
import soot.Value;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.NopStmt;
import soot.util.Chain;
import ast.Block;
import ast.BreakStmt;
import ast.ExprStmt;
import ast.IfStmt;
import ast.ReturnStmt;
import ast.Stmt;
import ast.Visitor;
import ast.WhileStmt;

/**
 * This class is in charge of creating Jimple code for a given statement (and its nested
 * statements, if applicable).
 */
public class StmtCodeGenerator extends Visitor<Void> {
	/** Cache Jimple singleton for convenience. */
	private final Jimple j = Jimple.v();
	
	/** The {@link FunctionCodeGenerator} that created this object. */
	private final FunctionCodeGenerator fcg;
	
	/** The statement list of the enclosing function body. */
	private final Chain<Unit> units;
	
	/** A map from while statements to their break target. */
	private final HashMap<WhileStmt, Unit> breakTargets = new HashMap<WhileStmt, Unit>();
	
	public StmtCodeGenerator(FunctionCodeGenerator fcg) {
		this.fcg = fcg;
		this.units = fcg.getBody().getUnits();
	}
	
	/** Generates code for an expression statement. */
	@Override
	public Void visitExprStmt(ExprStmt nd) {
		ExprCodeGenerator.generate(nd.getExpr(), fcg);
		return null;
	}
	
	/** Generates code for a break statement. */
	@Override
	public Void visitBreakStmt(BreakStmt nd) {
		// Step 1: First, we get the enclosing while loop
		var whileStatement = nd.getEnclosingLoop();

		// Step 2: Jump to the exit label associated with this loop
		var exitLabel = breakTargets.get(whileStatement);
		units.add(j.newGotoStmt(exitLabel));
		return null;
	}

	/** Generates code for a block of statements. */
	@Override
	public Void visitBlock(Block nd) {
		for(Stmt stmt : nd.getStmts())
			stmt.accept(this);
		return null;
	}
	
	/** Generates code for a return statement. */
	@Override
	public Void visitReturnStmt(ReturnStmt nd) {
		Unit stmt;
		if(nd.hasExpr())
			stmt = j.newReturnStmt(ExprCodeGenerator.generate(nd.getExpr(), fcg));
		else
			stmt = j.newReturnVoidStmt();
		units.add(stmt);
		return null;
	}
	
	/** Generates code for an if statement. */
	@Override
	public Void visitIfStmt(IfStmt nd) {
		Value cond = ExprCodeGenerator.generate(nd.getExpr(), fcg);
		NopStmt join = j.newNopStmt();
		units.add(j.newIfStmt(j.newEqExpr(cond, IntConstant.v(0)), join));
		nd.getThen().accept(this);
		if(nd.hasElse()) {
			NopStmt els = join;
			join = j.newNopStmt();
			units.add(j.newGotoStmt(join));
			units.add(els);
			nd.getElse().accept(this);
		}
		units.add(join);
		return null;
	}
		
	/** Generates code for a while statement. */
	@Override
	public Void visitWhileStmt(WhileStmt nd) {
		// Heavily follows lecture slides' pseudo code
		// We first adopt the pattern in the If statement above,
		// where we declare the NOPs and visit later to make them concrete.
		// These 2 NOPs represent the block within the while and the first statement following the block
		NopStmt within = j.newNopStmt();
		units.add(within);
		NopStmt following = j.newNopStmt();

		// If we break, we have to exit to the following block
		// NOTE: The lecture says there is continue (we jump back to within)
		// But this file doesn't seem to have it
		this.breakTargets.put(nd, following);

		// Step 1: First generate the condition and evaluate it
		var cond = ExprCodeGenerator.generate(nd.getExpr(), fcg);
		units.add(j.newIfStmt(j.newEqExpr(cond, IntConstant.v(0)), following));

		// Step 2: Generate code for body
		nd.getBody().accept(this);

		// Step 3: Add exit point
		units.add(j.newGotoStmt(within));
		units.add(following);
		return null;
	}
}
