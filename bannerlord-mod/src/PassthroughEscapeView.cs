using TaleWorlds.MountAndBlade.View.MissionViews;

namespace MinecraftPassthrough
{
	internal sealed class PassthroughEscapeView : MissionView
	{
		public PassthroughEscapeView()
		{
			ViewOrderPriority = -100;
		}

		public override bool OnEscape()
		{
			return PassthroughMissionLogic.Current?.CloseMinecraftScreen() == true;
		}
	}
}
