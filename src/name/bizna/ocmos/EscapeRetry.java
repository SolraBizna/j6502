package name.bizna.ocmos;

public class EscapeRetry extends RuntimeException {
	private static final long serialVersionUID = -456;
	private short pc;
	public EscapeRetry(short pc) {
		this.pc = pc;
	}
	public short getRetryPC() {
		return pc;
	}
}
