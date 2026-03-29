@file:Suppress("unused")

package org.eln2.mc.common.recipes.foundation

import com.google.gson.JsonObject
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.CriterionTriggerInstance
import net.minecraft.core.RegistryAccess
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.Level
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.LOG
import org.eln2.mc.common.recipes.RecipeRegistry
import org.eln2.mc.extensions.eln2Unlock
import org.eln2.mc.extensions.getInt
import java.util.function.Consumer

// Slot indices for the alloy smelter's inventory handler.
// INPUT_SLOT_A and INPUT_SLOT_B are the two metal ingredients;
// OUTPUT_SLOT is the resulting alloy.
const val ALLOY_INPUT_SLOT_A = 0
const val ALLOY_INPUT_SLOT_B = 1
const val ALLOY_OUTPUT_SLOT = 2

/**
 * Recipe for combining two metal ingredients into an alloy.
 * Can be used for an alloy smelter machine.
 *
 * @param inputA  The first input ingredient. Must be a single item stack with 1 count.
 * @param inputB  The second input ingredient. Must be a single item stack with 1 count.
 * @param output  The output alloy item. Must be a single item with 1 or more count.
 * @param duration The base duration, in seconds.
 * @param tier    The recipe's tier. By default, `0`.
 */
class AlloySmeltingRecipe(
    val recipeSerializer: Serializer,
    override val recipeId: ResourceLocation,
    val inputA: Ingredient,
    val inputB: Ingredient,
    override val output: ItemStack,
    override val duration: Double,
    override val tier: Int
) : Eln2SimpleOutputProcessingLoopRecipe, Eln2TieredRecipe {

    init {
        require(inputA.items.isNotEmpty() && inputA.items.all { it.count == 1 }) {
            DEBUGGER_BREAK("Alloy smelting recipe requires exactly one/one inputA!")
        }
        require(inputB.items.isNotEmpty() && inputB.items.all { it.count == 1 }) {
            DEBUGGER_BREAK("Alloy smelting recipe requires exactly one/one inputB!")
        }
    }

    /**
     * Matches when both input slots hold their respective ingredients.
     * The two inputs are order-independent: A can be in either slot.
     */
    override fun matches(pContainer: SimpleContainer, pLevel: Level): Boolean {
        val slotA = pContainer.getItem(ALLOY_INPUT_SLOT_A)
        val slotB = pContainer.getItem(ALLOY_INPUT_SLOT_B)
        return (inputA.test(slotA) && inputB.test(slotB)) ||
            (inputA.test(slotB) && inputB.test(slotA))
    }

    override fun assemble(pContainer: SimpleContainer, pRegistryAccess: RegistryAccess): ItemStack = output.copy()
    override fun canCraftInDimensions(pWidth: Int, pHeight: Int) = true
    override fun getResultItem(pRegistryAccess: RegistryAccess): ItemStack = output.copy()

    override fun getId() = recipeId
    override fun getSerializer() = recipeSerializer
    override fun getType() = recipeSerializer.recipeType

    class Serializer(val recipeType: RecipeType<AlloySmeltingRecipe>) :
        RecipeSerializer<AlloySmeltingRecipe> {

        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): AlloySmeltingRecipe {
            val inputA   = Ingredient.fromJson(pSerializedRecipe.get("ingredientA"))
            val inputB   = Ingredient.fromJson(pSerializedRecipe.get("ingredientB"))
            val output   = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "result"))
            val duration = pSerializedRecipe.getAsJsonPrimitive("duration").asDouble
            val tier     = pSerializedRecipe.getInt("tier", 0)

            return AlloySmeltingRecipe(this, pRecipeId, inputA, inputB, output, duration, tier)
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): AlloySmeltingRecipe {
            val inputA   = Ingredient.fromNetwork(pBuffer)
            val inputB   = Ingredient.fromNetwork(pBuffer)
            val output   = pBuffer.readItem()
            val duration = pBuffer.readDouble()
            val tier     = pBuffer.readInt()

            return AlloySmeltingRecipe(this, pRecipeId, inputA, inputB, output, duration, tier)
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: AlloySmeltingRecipe) {
            pRecipe.inputA.toNetwork(pBuffer)
            pRecipe.inputB.toNetwork(pBuffer)
            pBuffer.writeItem(pRecipe.output)
            pBuffer.writeDouble(pRecipe.duration)
            pBuffer.writeInt(pRecipe.tier)
        }
    }
}

class AlloySmeltingRecipeBuilder(val recipe: RecipeType<AlloySmeltingRecipe>) {
    private var inputA: Ingredient = Ingredient.EMPTY
    private var inputB: Ingredient = Ingredient.EMPTY
    private var output: ItemStack  = ItemStack.EMPTY
    private var duration: Double   = 10.0
    private var tier: Int          = 0
    val advancement: Advancement.Builder = Advancement.Builder.advancement()

    fun withInputA(input: ItemLike): AlloySmeltingRecipeBuilder {
        this.inputA = Ingredient.of(input)
        return this
    }

    fun withInputA(input: Ingredient): AlloySmeltingRecipeBuilder {
        this.inputA = input
        return this
    }

    fun withInputB(input: ItemLike): AlloySmeltingRecipeBuilder {
        this.inputB = Ingredient.of(input)
        return this
    }

    fun withInputB(input: Ingredient): AlloySmeltingRecipeBuilder {
        this.inputB = input
        return this
    }

    fun withOutput(output: ItemLike, count: Int = 1): AlloySmeltingRecipeBuilder {
        this.output = ItemStack(output, count)
        return this
    }

    fun withDuration(duration: Double): AlloySmeltingRecipeBuilder {
        this.duration = duration
        return this
    }

    fun withTier(tier: Int): AlloySmeltingRecipeBuilder {
        this.tier = tier
        return this
    }

    fun unlockedBy(pCriterionName: String, pCriterionTrigger: CriterionTriggerInstance): AlloySmeltingRecipeBuilder {
        advancement.addCriterion(pCriterionName, pCriterionTrigger)
        return this
    }

    fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
        check(!inputA.isEmpty) {
            DEBUGGER_BREAK("InputA for alloy smelting recipe cannot be empty")
        }

        check(!inputB.isEmpty) {
            DEBUGGER_BREAK("InputB for alloy smelting recipe cannot be empty")
        }

        check(!output.isEmpty) {
            DEBUGGER_BREAK("Output for alloy smelting recipe cannot be empty")
        }

        if (advancement.criteria.isEmpty()) {
            LOG.error("No criterion for alloy smelting recipe $id")
        } else {
            advancement.eln2Unlock(id)
        }

        consumer.accept(Result(this, id))
    }

    class Result(val parent: AlloySmeltingRecipeBuilder, val recipeId: ResourceLocation) : Eln2FinishedRecipe {
        override fun serializeRecipeData(json: JsonObject) {
            json.add("ingredientA", parent.inputA.toJson())
            json.add("ingredientB", parent.inputB.toJson())

            json.add("result", JsonObject().also { resultJson ->
                resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(parent.output.item)!!.toString())
                resultJson.addProperty("count", parent.output.count)
            })

            json.addProperty("duration", parent.duration)

            if (parent.tier != 0) {
                json.addProperty("tier", parent.tier)
            }
        }

        override fun getId(): ResourceLocation = recipeId
        override fun getType(): RecipeSerializer<*> = RecipeRegistry.getRecipeSerializer(parent.recipe)!!.get()
        override fun serializeAdvancement(): JsonObject = parent.advancement.serializeToJson()
    }
}
