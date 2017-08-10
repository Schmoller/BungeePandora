package au.com.addstar.bpandora;

public interface Module
{
	void onEnable();
	
	void onDisable();
	
	void setPandoraInstance(MasterPlugin plugin);
}
