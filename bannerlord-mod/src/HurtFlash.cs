using System.Collections.Generic;
using TaleWorlds.Engine;
using TaleWorlds.MountAndBlade;

namespace MinecraftPassthrough
{
	internal sealed class HurtFlash
	{
		private const float Duration = 0.5f;
		private const uint Tint = 0xFFFF4040;

		private readonly Dictionary<Agent, Flash> _flashing = new Dictionary<Agent, Flash>();
		private readonly List<Agent> _agents = new List<Agent>();

		public void Start(Agent agent)
		{
			MBAgentVisuals visuals = agent?.AgentVisuals;
			GameEntity entity = visuals?.GetEntity();
			if (entity == null)
			{
				return;
			}

			if (_flashing.TryGetValue(agent, out Flash flash))
			{
				flash.Left = Duration;
				_flashing[agent] = flash;
				return;
			}

			_flashing[agent] = new Flash { Left = Duration, Factor = entity.GetFactorColor(), Meshes = Colours(visuals, entity) };
			entity.SetFactorColor(Tint);
		}

		public void Tick(float dt)
		{
			if (_flashing.Count == 0)
			{
				return;
			}

			_agents.Clear();
			_agents.AddRange(_flashing.Keys);
			foreach (Agent agent in _agents)
			{
				Flash flash = _flashing[agent];
				flash.Left -= dt;
				if (flash.Left > 0f)
				{
					_flashing[agent] = flash;
				}
				else
				{
					Restore(agent, flash);
					_flashing.Remove(agent);
				}
			}
		}

		public void Stop(Agent agent)
		{
			if (_flashing.TryGetValue(agent, out Flash flash))
			{
				Restore(agent, flash);
				_flashing.Remove(agent);
			}
		}

		public void Forget(Agent agent)
		{
			_flashing.Remove(agent);
		}

		public void ForgetAll()
		{
			_flashing.Clear();
		}

		private static void Restore(Agent agent, Flash flash)
		{
			GameEntity entity = agent.AgentVisuals?.GetEntity();
			if (entity == null)
			{
				return;
			}
			entity.SetFactorColor(flash.Factor);
			foreach (MeshColour mesh in flash.Meshes)
			{
				mesh.Restore();
			}
		}

		private static List<MeshColour> Colours(MBAgentVisuals visuals, GameEntity entity)
		{
			var colours = new List<MeshColour>();
			Skeleton skeleton = visuals.GetSkeleton();
			if (skeleton != null)
			{
				int count = skeleton.GetComponentCount(GameEntity.ComponentType.MetaMesh);
				for (int i = 0; i < count; i++)
				{
					if (skeleton.GetComponentAtIndex(GameEntity.ComponentType.MetaMesh, i) is MetaMesh metaMesh)
					{
						Add(colours, metaMesh);
					}
				}
				count = skeleton.GetComponentCount(GameEntity.ComponentType.ClothSimulator);
				for (int i = 0; i < count; i++)
				{
					Add(colours, (skeleton.GetComponentAtIndex(GameEntity.ComponentType.ClothSimulator, i) as ClothSimulatorComponent)?.GetFirstMetaMesh());
				}
			}

			var entities = new List<GameEntity>();
			entity.GetChildrenRecursive(ref entities);
			entities.Add(entity);
			foreach (GameEntity part in entities)
			{
				for (int i = 0; i < part.MultiMeshComponentCount; i++)
				{
					Add(colours, part.GetMetaMesh(i));
				}
				for (int i = 0; i < part.ClothSimulatorComponentCount; i++)
				{
					Add(colours, part.GetClothSimulator(i)?.GetFirstMetaMesh());
				}
			}
			return colours;
		}

		private static void Add(List<MeshColour> colours, MetaMesh metaMesh)
		{
			if (metaMesh == null || !metaMesh.IsValid)
			{
				return;
			}
			for (int i = 0; i < metaMesh.MeshCount; i++)
			{
				Mesh mesh = metaMesh.GetMeshAtIndex(i);
				if (mesh != null && mesh.IsValid)
				{
					colours.Add(new MeshColour { Mesh = mesh, Color = mesh.Color, Color2 = mesh.Color2 });
				}
			}
		}

		private struct MeshColour
		{
			public Mesh Mesh;
			public uint Color;
			public uint Color2;

			public void Restore()
			{
				if (Mesh.IsValid)
				{
					Mesh.Color = Color;
					Mesh.Color2 = Color2;
				}
			}
		}

		private struct Flash
		{
			public float Left;
			public uint Factor;
			public List<MeshColour> Meshes;
		}
	}
}
