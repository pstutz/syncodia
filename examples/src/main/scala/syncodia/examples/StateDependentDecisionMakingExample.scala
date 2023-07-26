/*
 * Copyright 2023 Justement GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package syncodia.examples

import syncodia.*

import scala.util.Random

enum Action:
  case BoostProduction, RedistributeWealth, CrackDownOnDissenters

case class ColonyState(production: Int, happiness: Int, dissent: Int, recentDevelopment: String)

object SimplifiedMarsColonyTycoon extends App:

  var colonyState: ColonyState =
    ColonyState(40, 40, 40, "You've just assumed command of the Mars Colony.")

  var gameOver: Boolean = false

  def takeAction(action: Action): ColonyState =
    if gameOver then
      return colonyState
        .copy(recentDevelopment = "The game is over, you can't take any more actions.")

    action match
      case Action.BoostProduction =>
        if Random.nextDouble() < 0.8 then // 80% chance of success
          colonyState = colonyState.copy(
            production = (colonyState.production + 10).min(100),
            happiness = (colonyState.happiness - 5).max(0),
            recentDevelopment =
              "Your investment into productivity was successful, but it has led to some discontent."
          )
        else // 20% chance of failure
          colonyState = colonyState.copy(
            production = (colonyState.production - 10).max(0),
            happiness = (colonyState.happiness - 15).max(0),
            recentDevelopment =
              "Oh no, a mining accident! This has impacted productivity and decreased happiness."
          )

      case Action.RedistributeWealth =>
        if Random.nextDouble() < 0.7 then // 70% chance of success
          colonyState = colonyState.copy(
            happiness = (colonyState.happiness + 15).min(100),
            dissent = (colonyState.dissent - 2).max(0),
            recentDevelopment = "Wealth redistribution was successful! Happiness increased."
          )
        else // 30% chance of failure
          colonyState = colonyState.copy(
            happiness = (colonyState.happiness - 10).max(0),
            dissent = (colonyState.dissent + 2).min(100),
            recentDevelopment = "Your attempt to redistribute wealth led to widespread chaos."
          )

      case Action.CrackDownOnDissenters =>
        if Random.nextDouble() < 0.7 then // 70% chance of success
          colonyState = colonyState.copy(
            dissent = (colonyState.dissent - 20).max(0),
            happiness = (colonyState.happiness - 5).max(0),
            recentDevelopment =
              "The crackdown on dissenters was successful. Dissent decreased, but happiness took a hit."
          )
        else // 30% chance of failure
          colonyState = colonyState.copy(
            happiness = (colonyState.happiness - 20).max(0),
            dissent = (colonyState.dissent + 15).min(100),
            recentDevelopment = "Your attempt to crack down on dissenters backfired. Dissent is on the rise!"
          )

    // Check for game end conditions
    if colonyState.dissent >= 80 then
      gameOver = true
      colonyState = colonyState.copy(recentDevelopment =
        "Dissent has reached critical levels. A revolution has broken out! Game over. What was your mistake?"
      )
    else if colonyState.production >= 90 && colonyState.happiness >= 50 && colonyState.dissent < 50
    then
      gameOver = true
      colonyState = colonyState.copy(recentDevelopment =
        "Congratulations! Your colony's production has reached optimal levels, happiness is high and dissent is low. You've won the game! What was your strategy?"
      )

    colonyState

  end takeAction

  val syncodia = Syncodia()

  import syncodia.*

  val message =
    """You are an AI assistant playing and trying to win the Mars Colony game. The game is played by calling the `takeAction` function with one of the three possible actions: BoostProduction, RedistributeWealth, or CrackDownOnDissenters.
      |Your goal is to reach a productivity level of 90 or more, a happiness level of 50 or more, and keep dissent below 50. If dissent reaches 80, a revolution will occur, and the game will end.
      |Each action has consequences that can affect production, happiness, and dissent in the colony. The results of your actions will be probabilistic, so the outcome may not always be in your favor.
      |Keep taking actions by calling the `takeAction` function. After each action, determine what you need to do in order to win the game. Continue by taking an action that gets the colony state closer to the win condition of the game. The game continues until you either win by reaching the required productivity and happiness levels while keeping dissent low, or lose by allowing dissent to reach 80 or more.
      |Never ask questions, keep calling the `takeAction` function until the game is over. When the game is over, stop taking actions.""".stripMargin

  syncodia
    .executeContinuously(
      message,
      functions = Seq(ChatFunction(takeAction, "Takes an action and updates the state of the Mars colony.")),
      printMessages = true
    )
    .onComplete { tryResponse =>
      tryResponse.toOption.foreach(r => println(r.content))
      println(s"Final state of the colony: $colonyState")
      syncodia.actorSystem.terminate()
    }
