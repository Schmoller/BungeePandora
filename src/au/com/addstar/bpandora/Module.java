package au.com.addstar.bpandora;

public interface Module
{
	public void onEnable();
	
	public void onDisable();
	
	public void setPandoraInstance(MasterPlugin plugin);
}
